package de.bonndan.nivio.output.map.svg;

import de.bonndan.nivio.model.*;
import de.bonndan.nivio.output.layout.LayoutedComponent;
import de.bonndan.nivio.output.map.hex.Hex;
import de.bonndan.nivio.output.map.hex.PathFinder;
import j2html.tags.DomContent;
import j2html.tags.UnescapedText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static de.bonndan.nivio.output.map.svg.SVGItemLabel.LABEL_WIDTH;
import static de.bonndan.nivio.output.map.svg.SVGRenderer.DEFAULT_ICON_SIZE;
import static j2html.TagCreator.rawHtml;


/**
 * Creates an SVG document based on pre-rendered map items.
 */
public class SVGDocument extends Component {

    private static final Logger LOGGER = LoggerFactory.getLogger(SVGDocument.class);

    private final Set<Hex> occupied = new HashSet<>();
    private final LayoutedComponent layouted;
    private final LandscapeImpl landscape;
    private final String cssStyles;
    private boolean debug = false;

    public SVGDocument(@NonNull LayoutedComponent layouted, @Nullable String cssStyles) {
        this.layouted = Objects.requireNonNull(layouted);
        this.landscape = (LandscapeImpl) layouted.getComponent();
        this.cssStyles = StringUtils.isEmpty(cssStyles) ? "" : cssStyles;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public DomContent render() {

        Map<LandscapeItem, Hex> vertexHexes = new HashMap<>();
        List<DomContent> patterns = new ArrayList<>();
        List<DomContent> items = new ArrayList<>();

        //transform all item positions to hex map positions
        layouted.getChildren().forEach(group -> {
            group.getChildren().forEach(layoutedItem -> {
                Hex hex = null;
                int i = 0;
                while (hex == null || occupied.contains(hex)) {
                    hex = Hex.of(Math.round(layoutedItem.getX()) - i, Math.round(layoutedItem.getY()) - i);
                    i++;
                }

                Item item = (Item) layoutedItem.getComponent();
                hex.id = item.getFullyQualifiedIdentifier().jsonValue();
                vertexHexes.put(item, hex);
                occupied.add(hex);

                //collect patterns for icons
                if (!StringUtils.isEmpty(layoutedItem.getFill())) {
                    SVGPattern SVGPattern = new SVGPattern(layoutedItem.getFill());
                    patterns.add(SVGPattern.render());
                }

                //render icons
                SVGItemLabel label = new SVGItemLabel(item);
                Point2D.Double pos = vertexHexes.get(item).toPixel();

                SVGItem SVGItem = new SVGItem(label.render(), layoutedItem, pos);
                items.add(SVGItem.render());
            });
        });


        // find and render relations
        var pathFinder = new PathFinder(occupied);
        pathFinder.debug = this.debug;
        List<SVGRelation> relations = getRelations(layouted, vertexHexes, pathFinder);

        //generate group areas
        AtomicInteger width = new AtomicInteger(0);
        AtomicInteger height = new AtomicInteger(0);
        AtomicInteger minX = new AtomicInteger(Integer.MAX_VALUE);
        AtomicInteger minY = new AtomicInteger(Integer.MAX_VALUE);

        List<DomContent> groups = layouted.getChildren().stream().map(groupLayout -> {
            SVGGroupArea area = SVGGroupAreaFactory.getGroup(occupied, (Group) groupLayout.getComponent(), vertexHexes, relations);

            //fix viewport, because xy and hex coordinate system have different offsets
            area.groupArea.forEach(hex -> {
                var pos = hex.toPixel();
                if (pos.x < minX.get())
                    minX.set((int) pos.x);

                if (pos.y < minY.get())
                    minY.set((int) pos.y);

                //iterate all items to render them and collect max svg dimension
                // add extra margins size group area is larger than max item positions
                if (pos.x > width.get())
                    width.set((int) pos.x);
                if (pos.y > height.get())
                    height.set((int) pos.y);
            });

            return area.render();
        }).collect(Collectors.toList());

        int paddingTopLeft = 2 * Hex.HEX_SIZE;

        DomContent title = SvgTagCreator.text(landscape.getName())
                .attr("x", minX.get() - paddingTopLeft)
                .attr("y", minY.get() - paddingTopLeft + 40)
                .attr("class", "title");
        DomContent logo = null;
        String logoUrl = landscape.getConfig().getBranding().getMapLogo();
        if (!StringUtils.isEmpty(logoUrl)) {
            logo = SvgTagCreator.image()
                    .attr("xlink:href", logoUrl)
                    .attr("x", minX.get() - paddingTopLeft)
                    .attr("y", minY.get() - paddingTopLeft + 60)
                    .attr("width", LABEL_WIDTH)
                    .attr("height", LABEL_WIDTH);
        }


        UnescapedText style = rawHtml("<style>\n" + cssStyles + "</style>");


        int viewBoxPadding2 = 2 * Hex.HEX_SIZE;
        return SvgTagCreator.svg(style)
                .attr("version", "1.1")
                .attr("xmlns", "http://www.w3.org/2000/svg")
                .attr("xmlns:xlink", "http://www.w3.org/1999/xlink")
                .attr("width", width.addAndGet(DEFAULT_ICON_SIZE + LABEL_WIDTH / 2))
                .attr("height", height.addAndGet(DEFAULT_ICON_SIZE))
                .attr("viewBox", (minX.get() - paddingTopLeft) + " " + (minY.get() - paddingTopLeft) + " " + (width.get() + viewBoxPadding2) + " " + (height.get() + viewBoxPadding2))
                .attr("class", "map")

                .with(logo, title)
                .with(groups)
                .with(relations.stream().map(SVGRelation::render))
                .with(items)
                .with(SvgTagCreator.defs().with(patterns));
    }

    /**
     * Iterates over all items and invokes pathfinding for their relations.
     */
    private List<SVGRelation> getRelations(LayoutedComponent layouted, Map<LandscapeItem, Hex> vertexHexes, PathFinder pathFinder) {
        List<SVGRelation> relations = new ArrayList<>();
        layouted.getChildren().forEach(layoutedGroup -> {
            layoutedGroup.getChildren().forEach(layoutedItem -> {
                Item item = (Item) layoutedItem.getComponent();
                LOGGER.debug("Adding {} relations for {}", item.getRelations().size(), item.getFullyQualifiedIdentifier());
                item.getRelations().stream()
                        .filter(rel -> rel.getSource().equals(item)) //do not paint twice / incoming (inverse) relations
                        .map(rel -> getSvgRelation(vertexHexes, pathFinder, layoutedItem, item, rel))
                        .filter(Objects::nonNull)
                        .forEach(relations::add);
            });
        });

        return relations;
    }

    private SVGRelation getSvgRelation(Map<LandscapeItem, Hex> vertexHexes, PathFinder pathFinder, LayoutedComponent layoutedItem, Item item, RelationItem<Item> rel) {
        Hex start = vertexHexes.get(item);
        Hex target = vertexHexes.get(rel.getTarget());
        HexPath bestPath = pathFinder.getPath(start, target);
        if (bestPath != null) {
            SVGRelation svgRelation = new SVGRelation(bestPath, layoutedItem.getColor(), rel);
            LOGGER.debug("Added path for item {} relation {} -> {}", item, rel.getSource(), rel.getTarget());
            return svgRelation;
        }
        LOGGER.error("No path found for item {} relation {}", item, rel);
        return null;
    }

    public String getXML() {
        return render().render();
    }
}

