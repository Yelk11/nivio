package de.bonndan.nivio.output.jgraphx;

import com.mxgraph.canvas.mxGraphics2DCanvas;
import com.mxgraph.layout.mxOrganicLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGeometry;
import com.mxgraph.util.mxCellRenderer;
import com.mxgraph.util.mxConstants;
import com.mxgraph.util.mxRectangle;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import de.bonndan.nivio.landscape.*;
import de.bonndan.nivio.output.Icons;
import de.bonndan.nivio.output.Renderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.bonndan.nivio.landscape.Status.UNKNOWN;

public class JGraphXRenderer implements Renderer {
    private final int DEFAULT_ICON_SIZE = 40;

    private Logger logger = LoggerFactory.getLogger(JGraphXRenderer.class);
    private Map<Service, Object> serviceVertexes = new HashMap<>();
    private mxStylesheet stylesheet;
    private mxGraph graph;
    private Map<String, Object> groupNodes = new HashMap<>();

    @Override
    public String render(Landscape landscape) {
        return null;
    }

    @Override
    public void render(Landscape landscape, File file) throws IOException {
        graph = new mxGraph();
        graph.setHtmlLabels(true);

        stylesheet = graph.getStylesheet();
        mxGraphics2DCanvas.putShape("circularImage", new mxCircularImageShape());

        graph.getModel().beginUpdate();
        try {
            Groups groups = Groups.from(landscape);
            addGrouped(groups);
            addCommon(groups);

            addVirtualEdgesBetweenGroups(landscape.getServices());
            //organic layout between group containers
            mxOrganicLayout outer = new mxOrganicLayout(graph);
            outer.setEdgeLengthCostFactor(0.001D);
            outer.setNodeDistributionCostFactor(10000.0D);
            outer.execute(graph.getDefaultParent());

        } finally {


            graph.getModel().endUpdate();

            addInterGroupProviderEdges(landscape.getServices());

            //addGroupNodes extra stuff per service
            serviceVertexes.entrySet().forEach(this::renderExtras);

            //dataflow rendered after layout
            addDataFlow(landscape.getServices());

            addVirtualGroupNodes();

            //draw vertexes above edges
            graph.orderCells(false, serviceVertexes.values().toArray());

        }

        BufferedImage image = mxCellRenderer.createBufferedImage(graph, null, 1, Color.WHITE, true, null);

        ImageIO.write(image, "PNG", file);

    }

    /**
     * Adds copies of the group nodes with same size plus padding.
     * The original group nodes are for some reason not centered b theloweir children.
     */
    private void addVirtualGroupNodes() {

        List<Object> virtualNodes = new ArrayList<>();
        groupNodes.forEach((group, node) -> {
            mxCell cell = (mxCell) node;

            Object[] childCells = Arrays.stream(graph.getChildCells(cell))
                    .filter(o -> ((mxCell) o).isVertex())
                    .toArray();

            mxRectangle geo = graph.getBoundsForCells(childCells, false, false, false);

            if (geo == null)
                return;

            final String groupColor = group.startsWith(Groups.COMMON) ? de.bonndan.nivio.util.Color.GRAY
                    : de.bonndan.nivio.util.Color.nameToRGB(group);

            Object vg = graph.insertVertex(
                    graph.getDefaultParent(), group + "v", group,
                    geo.getX() - DEFAULT_ICON_SIZE, //more space because of labels
                    geo.getY() - DEFAULT_ICON_SIZE / 2,
                    geo.getWidth() + 2 * DEFAULT_ICON_SIZE,
                    geo.getHeight() + 2 * DEFAULT_ICON_SIZE,
                    "strokeColor=" + groupColor + ";" + "strokeWidth=3;rounded=1;"
                            + mxConstants.STYLE_FILLCOLOR + "=" + de.bonndan.nivio.util.Color.lighten(groupColor) + ";"
                            + mxConstants.STYLE_VERTICAL_ALIGN + "=" + mxConstants.ALIGN_BOTTOM + ";"
            );
            virtualNodes.add(vg);
        });
        graph.orderCells(true, virtualNodes.toArray());
    }

    /**
     * Adds only the edges which cross group boundaries.
     *
     * These edges are added after layouting in order not to influence it.
     *
     * @param services all services
     */
    private void addInterGroupProviderEdges(List<Service> services) {

        services.forEach(service -> {
            final String groupColor = de.bonndan.nivio.util.Color.nameToRGB(service.getGroup());
            final String astyle = mxConstants.STYLE_STROKEWIDTH + "=3;"
                    + mxConstants.STYLE_ENDARROW + "=oval;"
                    + mxConstants.STYLE_STARTARROW + "=false;"
                    + mxConstants.STYLE_STROKECOLOR + "=#" + groupColor + ";";

            service.getProvidedBy().forEach(provider -> {

                if (!service.getGroup().equals(provider.getGroup())) {
                    graph.insertEdge(
                            graph.getDefaultParent(), null, "",
                            serviceVertexes.get(provider),
                            serviceVertexes.get(service),
                            astyle
                    );
                }
            });
        });
    }

    /**
     * Adds dataflow edges.
     *
     */
    private void addDataFlow(List<Service> services) {

        services.forEach(service -> service.getDataFlow().forEach(df -> {

            if (df.getSource().equals(df.getTarget()))
                return;

            String id = "df_" + service.getIdentifier() + df.getTarget();
            logger.info("Adding dataflow " + id);
            ServiceItem target = ServiceItems.find(FullyQualifiedIdentifier.from(df.getTarget()), services);
            graph.insertEdge(graph.getDefaultParent(), id, df.getFormat(), serviceVertexes.get(service), serviceVertexes.get(target),
                    mxConstants.STYLE_STROKECOLOR + "=#" + getGroupColor(service) + ";"
                            + mxConstants.STYLE_STROKEWIDTH + "=2;"
                            + mxConstants.STYLE_DASHED + "=true;"
                            + mxConstants.STYLE_VERTICAL_LABEL_POSITION + "=bottom;"
            );
        }));
    }

    /**
     * Virtual edges between group containers enable organic layout of groups.
     */
    private void addVirtualEdgesBetweenGroups(List<Service> services) {
        services.forEach(service -> {
            String group = service.getGroup();
            Object groupNode = groupNodes.get(group);
            service.getProvidedBy().forEach(provider -> {
                String pGroup = provider.getGroup() == null ? Groups.COMMON : provider.getGroup();
                Object pGroupNode = groupNodes.get(pGroup);
                if (Groups.COMMON.equals(pGroup)) {
                    pGroupNode = groupNodes.get(Groups.COMMON + provider.getLayer());
                }
                if (pGroupNode != null && pGroupNode != groupNode) {
                    graph.insertEdge(graph.getDefaultParent(), "", "", groupNode, pGroupNode,
                            mxConstants.STYLE_STROKEWIDTH + "=0;"
                    );
                }
            });

        });
    }

    /**
     * Adds group nodes (parent) and all children.
     *
     * @param groups groups
     */
    private void addGrouped(Groups groups) {
        groups.getAll().forEach((groupName, serviceItems) -> {

            if (Groups.COMMON.equals(groupName))
                return;

            Object groupnode = graph.insertVertex(
                    graph.getDefaultParent(),
                    groupName,
                    "",
                    0, 0, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                    "strokeColor=none;strokeWidth=0;" + mxConstants.STYLE_FILLCOLOR + "=none;"
            );
            groupNodes.put(groupName, groupnode);

            addGroupItems(graph, groupnode, serviceItems);

            //organic layout inside the group/layer nodes
            mxOrganicLayout inner = new mxOrganicLayout(graph);
            inner.setEdgeLengthCostFactor(0.001D);
            inner.setNodeDistributionCostFactor(10000.0D);
            inner.execute(groupnode);
            resizeContainer((mxCell) groupnode);

        });
    }

    private void addCommon(Groups groups) {

        List<ServiceItem> commonItems = groups.getAll().get(Groups.COMMON);

        final String noStyle = "strokeWidth=0;" + mxConstants.STYLE_FILLCOLOR + "=none;"
                + mxConstants.STYLE_STROKECOLOR + "=none";

        Object infra = graph.insertVertex(
                graph.getDefaultParent(),
                ServiceItem.LAYER_INFRASTRUCTURE,
                "",
                0, 0, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                noStyle
        );
        groupNodes.put(Groups.COMMON + " " + ServiceItem.LAYER_INFRASTRUCTURE, infra);

        Object ingress = graph.insertVertex(
                graph.getDefaultParent(),
                ServiceItem.LAYER_INGRESS,
                "",
                0, 0, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                noStyle
        );
        groupNodes.put(Groups.COMMON + " " + ServiceItem.LAYER_INGRESS, ingress);

        Object apps = graph.insertVertex(
                graph.getDefaultParent(),
                ServiceItem.LAYER_APPLICATION,
                "",
                0, 0, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                noStyle
        );
        groupNodes.put(Groups.COMMON + " " + ServiceItem.LAYER_APPLICATION, apps);

        commonItems.forEach(serviceItem -> {

            String style = getBaseStyle((Service) serviceItem) + ";"
                    + "strokeColor=" + getGroupColor((Service) serviceItem) + ";"
                    + "strokeWidth=3;";

            if (serviceItem.getLayer().equals(ServiceItem.LAYER_INGRESS)) {
                var vertex = addServiceVertex(ingress, serviceItem, style);
                serviceVertexes.put((Service) serviceItem, vertex);
            }

            if (serviceItem.getLayer().equals(ServiceItem.LAYER_INFRASTRUCTURE)) {
                var vertex = addServiceVertex(infra, serviceItem, style);
                serviceVertexes.put((Service) serviceItem, vertex);
            }

            if (serviceItem.getLayer().equals(ServiceItem.LAYER_APPLICATION)) {
                var vertex = addServiceVertex(apps, serviceItem, style);
                serviceVertexes.put((Service) serviceItem, vertex);
            }
        });

        mxOrganicLayout inner = new mxOrganicLayout(graph);
        inner.setEdgeLengthCostFactor(0.001D);
        inner.setNodeDistributionCostFactor(10000.0D);

        inner.execute(ingress);
        resizeContainer((mxCell) ingress);
        inner.execute(infra);
        resizeContainer((mxCell) infra);
    }

    private void resizeContainer(mxCell cell) {
        Object[] childCells = Arrays.stream(graph.getChildCells(cell)).filter(o -> ((mxCell) o).isVertex()).toArray();

        mxRectangle geo = graph.getBoundingBoxFromGeometry(childCells);
        geo.setWidth(geo.getWidth() + 2 * DEFAULT_ICON_SIZE);
        geo.setHeight(geo.getHeight() + 2 * DEFAULT_ICON_SIZE);

        graph.resizeCell(cell, geo);
        Arrays.stream(childCells).forEach(o -> {
            mxCell child = ((mxCell) o);
            if (StringUtils.isEmpty(child.getValue()))
                return;
            mxGeometry childGeo = child.getGeometry();
            childGeo.setX(childGeo.getX() - geo.getWidth());
            childGeo.setY(childGeo.getY() - geo.getHeight() - DEFAULT_ICON_SIZE);
        });
    }

    private Object addServiceVertex(Object parent, ServiceItem serviceItem, String style) {
        return graph.insertVertex(
                parent,
                serviceItem.getFullyQualifiedIdentifier().toString(),
                StringUtils.isEmpty(serviceItem.getName()) ? serviceItem.getIdentifier() : serviceItem.getName(),
                0, 0, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                style
        );
    }

    private void addGroupItems(mxGraph graph, Object parent, List<ServiceItem> groupItems) {

        groupItems.forEach(service -> {

            String style = getBaseStyle((Service) service) + ";"
                    + "strokeColor=" + getGroupColor((Service) service) + ";"
                    + "strokeWidth=3;";

            Object v1 = graph.insertVertex(
                    parent,
                    service.getFullyQualifiedIdentifier().toString(),
                    StringUtils.isEmpty(service.getName()) ? service.getIdentifier() : service.getName(),
                    20, 20, DEFAULT_ICON_SIZE, DEFAULT_ICON_SIZE,
                    style
            );
            serviceVertexes.put((Service) service, v1);
        });

        //inner group relations
        groupItems.forEach(service -> {
            final String groupColor = de.bonndan.nivio.util.Color.nameToRGB(service.getGroup());
            var astyle = mxConstants.STYLE_STROKEWIDTH + "=3;"
                    + mxConstants.STYLE_ENDARROW + "=oval;"
                    + mxConstants.STYLE_STARTARROW + "=false;"
                    + mxConstants.STYLE_STROKECOLOR + "=#" + groupColor + ";";

            ((Service) service).getProvidedBy().forEach(provider -> {

                if (service.getGroup().equals(provider.getGroup())) {
                    graph.insertEdge(
                            parent, null, "",
                            serviceVertexes.get(provider),
                            serviceVertexes.get(service),
                            astyle
                    );
                }
            });
        });
    }

    private void renderExtras(Map.Entry<Service, Object> entry) {
        mxCell cell = (mxCell) entry.getValue();
        mxRectangle cellBounds = graph.getCellBounds(cell);

        //sort services
        Service service = entry.getKey();
        List<StatusItem> displayed = service.getStatuses().stream()
                .filter(item -> !UNKNOWN.equals(item.getStatus()))
                .sorted((statusItem, t1) -> {
                    if (statusItem.getStatus().equals(t1.getStatus())) {
                        return statusItem.getLabel().compareToIgnoreCase(t1.getLabel());
                    }
                    return statusItem.getStatus().isHigherThan(t1.getStatus()) ? -1 : 1;
                }).collect(Collectors.toList());

        //statuses at left
        int statusBoxSize = 15;
        double statusOffsetX = cellBounds.getX() - 2.5 * statusBoxSize;
        AtomicReference<Double> statusOffsetY = new AtomicReference<>(cellBounds.getY());
        displayed.forEach(statusItem -> {
            graph.insertVertex(graph.getDefaultParent(), null,
                    statusItem.getLabel().toUpperCase().substring(0, 3),
                    statusOffsetX, statusOffsetY.get(), statusBoxSize * 2, statusBoxSize,
                    mxConstants.STYLE_FILLCOLOR + "=" + statusItem.getStatus().toString() + ";"
                            + mxConstants.STYLE_FONTCOLOR + "=black"
            );
            statusOffsetY.updateAndGet(v -> v + statusBoxSize);
        });


        //status ring
        String statusColor = getStatusColor(service);
        int statusRingWidth = 4;
        if (!Status.UNKNOWN.toString().equals(statusColor)) {
            String style = mxConstants.STYLE_FILLCOLOR + "=none;"
                    + mxConstants.STYLE_STROKEWIDTH + "=" + statusRingWidth + ";"
                    + mxConstants.STYLE_SHAPE + "=" + mxConstants.SHAPE_ELLIPSE + ";"
                    + mxConstants.STYLE_STROKECOLOR + "=" + statusColor + ";";
            graph.insertVertex(graph.getDefaultParent(), null,
                    "",
                    cellBounds.getX() - statusRingWidth / 2, cellBounds.getY() - statusRingWidth / 2, cellBounds.getWidth() + statusRingWidth, cellBounds.getHeight() + statusRingWidth,
                    style
            );
        }


        //interfaces
        int intfBoxSize = 10;
        double intfOffsetX = cellBounds.getCenterX() - 0.5 * intfBoxSize;
        double intfOffsetY = cellBounds.getCenterY() - 0.5 * intfBoxSize;
        String intfStyle = mxConstants.STYLE_SHAPE + "=" + mxConstants.SHAPE_ELLIPSE + ";"
                + mxConstants.STYLE_FONTCOLOR + "=black;"
                + mxConstants.STYLE_LABEL_POSITION + "=right;"
                + mxConstants.STYLE_ALIGN + "=left;"
                + mxConstants.STYLE_FILLCOLOR + "=#" + getGroupColor(service) + ";"
                + mxConstants.STYLE_STROKEWIDTH + "=0;";


        AtomicInteger count = new AtomicInteger(0);
        Stream<InterfaceItem> sorted = service.getInterfaces().stream()
                .sorted((interfaceItem, t1) -> interfaceItem.getDescription().compareToIgnoreCase(t1.getDescription()));
        sorted.forEach(intf -> {

            double angleInRadians = -1 + count.getAndIncrement() * 0.5;
            double radius = intfBoxSize * 4;
            double x = Math.cos(angleInRadians) * radius;
            double y = Math.sin(angleInRadians) * radius;

            Object v1 = graph.insertVertex(graph.getDefaultParent(), null,
                    intf.getDescription() + " (" + intf.getFormat() + ")",
                    intfOffsetX + x, intfOffsetY + y, intfBoxSize, intfBoxSize,
                    intfStyle
            );

            graph.insertEdge(
                    graph.getDefaultParent(), null, "",
                    cell,
                    v1,
                    mxConstants.STYLE_ENDARROW + "=none;"
                            + mxConstants.STYLE_STROKEWIDTH + "=2;"
                            + mxConstants.STYLE_STROKECOLOR + "=#" + getGroupColor(service) + ";"
            );
        });
    }

    private String getStatusColor(Service service) {
        var ref = new Object() {
            Status current = Status.UNKNOWN;
        };

        service.getStatuses().forEach(statusItem -> {
            if (statusItem.getStatus().isHigherThan(ref.current))
                ref.current = statusItem.getStatus();
        });

        return ref.current.toString();
    }

    private String getGroupColor(Service service) {
        return de.bonndan.nivio.util.Color.nameToRGB(service.getGroup(), "333333");
    }


    private String getBaseStyle(Service service) {
        String type = Icons.getIcon(service);

        if (!stylesheet.getStyles().containsKey(type)) {
            Hashtable<String, Object> style = new Hashtable<String, Object>();
            style.put(mxConstants.STYLE_SHAPE, "circularImage");
            style.put(mxConstants.STYLE_IMAGE, "http://localhost:8080/icons/" + type + ".png");
            style.put(mxConstants.STYLE_VERTICAL_LABEL_POSITION, mxConstants.ALIGN_BOTTOM);
            style.put(mxConstants.STYLE_LABEL_BACKGROUNDCOLOR, "#666");
            style.put(mxConstants.STYLE_FONTCOLOR, "white");
            style.put(mxConstants.STYLE_FILLCOLOR, "white");

            stylesheet.putCellStyle(type, style);
        }

        return type;
    }
}
