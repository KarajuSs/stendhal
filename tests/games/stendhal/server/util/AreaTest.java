package games.stendhal.server.util;

import games.stendhal.server.core.engine.StendhalRPWorld;
import games.stendhal.server.core.engine.StendhalRPZone;
import games.stendhal.server.entity.creature.Creature;

import java.awt.Rectangle;

import org.junit.Assert;
import org.junit.Test;


/**
 * Tests for the area class.
 *
 * @author hendrik
 */
public class AreaTest {
	private static final Rectangle rect = new Rectangle(3, 4, 5, 6);
	private static final StendhalRPZone zone = new StendhalRPZone("int_admin_test");
	private static final StendhalRPZone otherZone = new StendhalRPZone("int_admin_test_2");

	private Area createArea() {
		Area area = new Area(zone, rect);
		return area;
	}
	
	@Test
	public void testCreateArea() {
		Area area = createArea();
		Assert.assertEquals(rect, area.getShape());
	}

	@Test
	public void testInArea() {
		StendhalRPWorld.get();
		Area area = createArea();
		Creature entity = new Creature();
		zone.add(entity);
		entity.setPosition(3, 4);
		Assert.assertTrue(area.contains(entity));
	}

	@Test
	public void testNotInArea() {
		StendhalRPWorld.get();
		Area area = createArea();

		// other zone
		Creature entity = new Creature();
		otherZone.add(entity);
		entity.setPosition(3, 4);
		Assert.assertFalse(area.contains(entity));

		// right zone but wrong position
		entity = new Creature();
		zone.add(entity);
		entity.setPosition(1, 1);
		Assert.assertFalse(area.contains(entity));
}
}
