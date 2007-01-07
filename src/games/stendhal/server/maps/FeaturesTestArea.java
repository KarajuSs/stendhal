package games.stendhal.server.maps;

import games.stendhal.common.Direction;
import games.stendhal.server.RespawnPoint;
import games.stendhal.server.StendhalRPWorld;
import games.stendhal.server.StendhalRPZone;
import games.stendhal.server.entity.Entity;
import games.stendhal.server.entity.RPEntity;
import games.stendhal.server.entity.creature.AttackableCreature;
import games.stendhal.server.entity.creature.Creature;
import games.stendhal.server.entity.item.Item;
import games.stendhal.server.entity.portal.LockedDoor;
import games.stendhal.server.entity.portal.Portal;
import games.stendhal.server.rule.defaultruleset.DefaultEntityManager;
import games.stendhal.server.rule.defaultruleset.DefaultItem;

import java.util.LinkedList;
import java.util.List;

import marauroa.common.game.IRPZone;

public class FeaturesTestArea implements IContent {
	private StendhalRPZone zone;
	private DefaultEntityManager manager;
	

	static class QuestRat extends Creature {
		public QuestRat(Creature copy) {
			super(copy);
		}

		@Override
		public void onDead(Entity killer) {
			if (killer instanceof RPEntity) {
				RPEntity killerRPEntity = (RPEntity) killer;
				if (!killerRPEntity.isEquipped("key_golden")) {
					Item item = StendhalRPWorld.get().getRuleManager().getEntityManager().getItem(
							"key_golden");
					killerRPEntity.equip(item, true);
				}
			}
			super.onDead(killer);
		}

		@Override
		public void update() {
			noises = new LinkedList<String>(noises);
			noises.add("Thou shall not obtain the key!");
		}
	}

	public FeaturesTestArea() {
		zone = (StendhalRPZone) StendhalRPWorld.get().getRPZone(new IRPZone.ID(
				"int_pathfinding"));
		manager = (DefaultEntityManager) StendhalRPWorld.get()
				.getRuleManager().getEntityManager();
		
		createDoorAndKey();
		attackableAnimal();
	}
	
	private void createDoorAndKey() {
		Portal portal = new LockedDoor("key_golden", "skulldoor", Direction.DOWN);
		zone.assignRPObjectID(portal);
		portal.setX(50);
		portal.setY(10);
		portal.setNumber(0);
		portal.setDestination("int_pathfinding", 1);
		zone.addPortal(portal);

		portal = new Portal();
		zone.assignRPObjectID(portal);
		portal.setX(50);
		portal.setY(12);
		portal.setNumber(1);
		portal.setDestination("int_pathfinding", 0);
		zone.addPortal(portal);

		List<String> slots = new LinkedList<String>();
		slots.add("bag");

		DefaultItem item = new DefaultItem("key", "gold", "key_golden", -1);
		item.setImplementation(Item.class);
		item.setWeight(1);
		item.setEquipableSlots(slots);
		manager.addItem(item);

		item = new DefaultItem("key", "golden", "key_silver", -1);
		item.setImplementation(Item.class);
		item.setWeight(1);
		item.setEquipableSlots(slots);
		manager.addItem(item);

		Creature creature = new QuestRat(manager.getCreature("rat"));
		RespawnPoint point = new RespawnPoint(zone, 40, 5, creature, 1);
		zone.addRespawnPoint(point);
	}
	
	
	private void attackableAnimal() {

		Creature creature = new AttackableCreature(manager.getCreature("orc"));
		RespawnPoint point = new RespawnPoint(zone, 4, 56, creature, 1);
		point.setRespawnTime(60*60*3);
		zone.addRespawnPoint(point);

		creature = manager.getCreature("deer");
		point = new RespawnPoint(zone, 14, 56, creature, 1);
		point.setRespawnTime(60*60*3);
		zone.addRespawnPoint(point);
	}
}
