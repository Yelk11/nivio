package de.bonndan.nivio.output.map.svg;

import de.bonndan.nivio.model.Group;
import de.bonndan.nivio.model.Item;
import de.bonndan.nivio.model.LandscapeItem;
import de.bonndan.nivio.output.map.hex.GroupAreaFactory;
import de.bonndan.nivio.output.map.hex.Hex;
import j2html.tags.ContainerTag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SVGGroupAreaOutlineFactoryTest {


    @Test
    public void twoSeparateIslands() {
        Hex e1 = new Hex(0, 10, -10);
        Hex e2 = new Hex(0, 20, -20);
        Set<Hex> occupied = Set.of(e1, e2);

        Item item1 = new Item("foo", "bar");
        Item item2 = new Item("foo", "baz");

        Group foo = new Group("foo");
        foo.setColor("005500");
        foo.addItem(item1);
        foo.addItem(item2);

        Map<LandscapeItem, Hex> map = new HashMap<>();
        map.put(item1, e1);
        map.put(item2, e2);

        Set<Hex> area = GroupAreaFactory.getGroup(occupied, foo, map, new ArrayList<>());

        SVGGroupArea group = SVGGroupAreaFactory.getGroup(foo, area);
        Set<Hex> groupArea = group.groupArea;

        //when
        SVGGroupAreaOutlineFactory svgGroupAreaOutlineFactory = new SVGGroupAreaOutlineFactory(groupArea);
        List<ContainerTag> outline = svgGroupAreaOutlineFactory.getOutline("005500");

        //then
        assertNotNull(outline);
        assertEquals(2, outline.size());
    }

}