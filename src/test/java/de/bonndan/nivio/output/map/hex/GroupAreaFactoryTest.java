package de.bonndan.nivio.output.map.hex;

import de.bonndan.nivio.model.*;
import de.bonndan.nivio.output.map.svg.HexPath;
import de.bonndan.nivio.output.map.svg.SVGGroupAreaFactory;
import org.apache.commons.collections4.set.UnmodifiableSet;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupAreaFactoryTest {

    private final Set<Hex> expectedTerritory = UnmodifiableSet.unmodifiableSet(Set.of(
            new Hex(0, 3, -3),
            new Hex(1, 3, -4),
            new Hex(1, 2, -3),
            new Hex(2, 2, -4),
            new Hex(0, 2, -2),
            new Hex(2, 1, -3),
            new Hex(1, 1, -2)
    ));

    /**
     * https://www.redblobgames.com/grids/hexagons/#coordinates
     */
    @Test
    public void getBridges() {
        Set<Hex> inArea = new HashSet<>();
        //vertical with one hex gap
        inArea.add(new Hex(3, 1, -4));
        inArea.add(new Hex(3, 3, -6));

        //when
        Set<Hex> bridges = GroupAreaFactory.getBridges(inArea);
        assertEquals(1, bridges.size());
        assertEquals(new Hex(3, 2, -5), bridges.iterator().next());
    }

    @Test
    public void addHexAndPaths() {
        Set<Hex> occupied = new HashSet<>();
        occupied.add(new Hex(1, 1, -2));
        occupied.add(new Hex(3, 3, -6));

        Item landscapeItem = new Item("group", "landscapeItem");
        Item target = new Item("group", "target");

        Map<LandscapeItem, Hex> vertexHexes = Map.of(landscapeItem, new Hex(1, 2, -3));
        Hex landscapeItemHex = new Hex(4, 5, -9);
        HexPath hexPath = new HexPath(List.of(landscapeItemHex));
        hexPath.setGroup("group");

        Group group = new Group("group");
        group.addItem(landscapeItem);

        //when
        Set<Hex> inArea = GroupAreaFactory.getGroup(occupied, group, vertexHexes, List.of(hexPath));

        //then
        assertThat(inArea).containsAll(expectedTerritory);
        assertThat(inArea).contains(landscapeItemHex);
    }

    @Test
    public void justAddsHexAndNeighbours() {
        Set<Hex> occupied = new HashSet<>();
        occupied.add(new Hex(1, 1, -2));
        occupied.add(new Hex(3, 3, -6));

        Item landscapeItem = new Item("group", "landscapeItem");

        Map<LandscapeItem, Hex> vertexHexes = Map.of(landscapeItem, new Hex(1, 2, -3));

        Group group = new Group("group");
        group.addItem(landscapeItem);

        //when
        Set<Hex> inArea = GroupAreaFactory.getGroup(occupied, group, vertexHexes, new ArrayList<>());

        //then
        assertThat(inArea).isEqualTo(expectedTerritory);
    }

}
