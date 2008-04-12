/* $Id$ */
/***************************************************************************
 *                      (C) Copyright 2003 - Marauroa                      *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.client;

import games.stendhal.client.entity.Entity;
import games.stendhal.client.events.PositionChangeListener;
import games.stendhal.client.gui.Desktop;
import games.stendhal.client.gui.FormatTextParser;
import games.stendhal.client.gui.j2d.Text;
import games.stendhal.client.gui.j2d.entity.Entity2DView;
import games.stendhal.client.gui.j2d.entity.Entity2DViewFactory;
import games.stendhal.client.gui.wt.GroundContainer;
import games.stendhal.client.sprite.ImageSprite;
import games.stendhal.client.sprite.Sprite;
import games.stendhal.client.sprite.SpriteStore;
import games.stendhal.common.NotificationType;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Transparency;
import java.awt.font.TextAttribute;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.text.AttributedString;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * The game screen. This manages and renders the visual elements of the game.
 */
public class GameScreen implements PositionChangeListener, IGameScreen {

	/** the logger instance. */
	private static final Logger logger = Logger.getLogger(GameScreen.class);

	/**
	 * Comparator used to sort entities to display.
	 */
	protected static final EntityViewComparator entityViewComparator = new EntityViewComparator();

	/**
	 * A scale factor for panning delta (to allow non-float precision).
	 */
	protected static final int PAN_SCALE = 8;

	/**
	 * Graphics context.
	 */
	private Graphics2D g;

	/**
	 * The ground layer.
	 */
	private GroundContainer ground;

	/**
	 * The desktop background displaying the game screen.
	 */
	private final Desktop canvas;

	/**
	 * Static game layers.
	 */
	protected StaticGameLayers gameLayers;

	/**
	 * The text bubbles.
	 */
	private LinkedList<Text> texts;

	/**
	 * The text bubbles to remove.
	 */
	private List<Text> textsToRemove;

	/**
	 * The entity views.
	 */
	protected List<Entity2DView> views;

	/**
	 * The entity to view map.
	 */
	protected Map<Entity, Entity2DView> entities;

	private static Sprite offlineIcon;

	private boolean offline;

	private int blinkOffline;

	/**
	 * The targeted center of view (truncated).
	 */
	private Point center;

	/** Actual size of the screen in pixels. */
	private Dimension size;

	/** Actual size of the world in world units. */
	protected Dimension worldSize;

	/** the singleton instance. */
	private static IGameScreen screen;

	/**
	 * The difference between current and target screen view.
	 */
	private Point dv;

	/**
	 * The current screen view coordinate.
	 */
	private Point sv;

	/**
	 * The pan speed.
	 */
	private int speed;

	static {
		offlineIcon = SpriteStore.get().getSprite("data/gui/offline.png");
	}

	/**
	 * Set the default [singleton] screen.
	 *
	 * @param screen
	 *            The screen.
	 */
	public static void setDefaultScreen(IGameScreen screen) {
		GameScreen.screen = screen;
	}

	/** @return the GameScreen object. */
	public static IGameScreen get() {
		return screen;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getViewSize()
	 */
	public Dimension getViewSize() {
		return new Dimension(size.width / SIZE_UNIT_PIXELS, size.height / SIZE_UNIT_PIXELS);
	}

	/**
	 * Create a game screen.
	 *
	 * @param client
	 *            The client.
	 * @param canvas
	 *            The canvas to render in.
	 */
	public GameScreen(StendhalClient client, Desktop desktop) {
		gameLayers = client.getStaticGameLayers();
		canvas = desktop;

		size = desktop.getBufferSize();
		Point offset = desktop.getOffset();

		center = new Point(0, 0);
		sv = new Point(size.width / -2, size.height / -2);
		dv = new Point(offset.x, offset.y);

		speed = 0;

		texts = new LinkedList<Text>();
		textsToRemove = new LinkedList<Text>();
		views = new LinkedList<Entity2DView>();
		entities = new HashMap<Entity, Entity2DView>();

		// create ground
		ground = new GroundContainer(desktop, client, this, size);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#nextFrame()
	 */
	public void nextFrame() {
		g.dispose();
		canvas.repaint();

		adjustView();

		g = canvas.getDrawingBuffer();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#addEntity(games.stendhal.client.entity.Entity)
	 */
	public void addEntity(Entity entity) {
		Entity2DView view = createView(entity);

		if (view != null) {
			entities.put(entity, view);
			addEntityView(view);
		}
	}

	/**
	 * Add an entity view.
	 *
	 * @param view
	 *            A view.
	 */
	protected void addEntityView(Entity2DView view) {
		views.add(view);

		view.setInspector(ground);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#removeEntity(games.stendhal.client.entity.Entity)
	 */
	public void removeEntity(final Entity entity) {
		Entity2DView view = entities.remove(entity);

		if (view != null) {
			removeEntityView(view);
		}
	}

	/**
	 * Remove an entity view.
	 *
	 * @param view
	 *            A view.
	 */
	protected void removeEntityView(Entity2DView view) {
		view.release();
		views.remove(view);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#expose()
	 */
	public Graphics2D expose() {
		return g;
	}

	/**
	 * Update the view position to center the target position.
	 */
	protected void adjustView() {
		/*
		 * Already centered?
		 */
		if ((dv.x == 0) && (dv.y == 0)) {
			return;
		}

		Point c = convertWorldToScreenView(center);

		int sx = c.x + (SIZE_UNIT_PIXELS / 2);
		int sy = c.y + (SIZE_UNIT_PIXELS / 2);

		if ((sx < 0) || (sx >= size.width) || (sy < -SIZE_UNIT_PIXELS) || (sy > size.height)) {
			/*
			 * If off screen, just center
			 */
			center();
		} else {
			/*
			 * Calculate the target speed. The farther away, the faster.
			 */
			int dux = dv.x / 40;
			int duy = dv.y / 40;

			int tspeed = ((dux * dux) + (duy * duy)) * PAN_SCALE;

			if (speed > tspeed) {
				speed = (speed + speed + tspeed) / 3;

				/*
				 * Don't stall
				 */
				if ((dv.x != 0) || (dv.y != 0)) {
					speed = Math.max(speed, 1);
				}
			} else if (speed < tspeed) {
				speed += 2;
			}

			/*
			 * Moving?
			 */
			if (speed != 0) {
				/*
				 * Not a^2 + b^2 = c^2, but good enough
				 */
				int scalediv = (Math.abs(dv.x) + Math.abs(dv.y)) * PAN_SCALE;

				int dx = speed * dv.x / scalediv;
				int dy = speed * dv.y / scalediv;

				/*
				 * Don't overshoot. Don't stall.
				 */
				if (dv.x < 0) {
					if (dx == 0) {
						dx = -1;
					} else if (dx < dv.x) {
						dx = dv.x;
					}
				} else if (dv.x > 0) {
					if (dx == 0) {
						dx = 1;
					} else if (dx > dv.x) {
						dx = dv.x;
					}
				}

				if (dv.y < 0) {
					if (dy == 0) {
						dy = -1;
					} else if (dy < dv.y) {
						dy = dv.y;
					}
				} else if (dv.y > 0) {
					if (dy == 0) {
						dy = 1;
					} else if (dy > dv.y) {
						dy = dv.y;
					}
				}

				/*
				 * Adjust view
				 */
				sv.x += dx;
				dv.x -= dx;

				sv.y += dy;
				dv.y -= dy;
			}
		}
	}

	/**
	 * Updates the view position to center the target position.
	 *
	 */
	protected void calculateView() {
		int cvx = (center.x * SIZE_UNIT_PIXELS) + (SIZE_UNIT_PIXELS / 2) - (size.width / 2);
		int cvy = (center.y * SIZE_UNIT_PIXELS) + (SIZE_UNIT_PIXELS / 2) - (size.height / 2);

		/*
		 * Keep the world with-in the screen view
		 */
		if (cvx < 0) {
			cvx = 0;
		} else {
			int max = (worldSize.width * SIZE_UNIT_PIXELS) - size.width;

			if (cvx > max) {
				cvx = max;
			}
		}

		if (cvy < 0) {
			cvy = 0;
		} else {
			int max = (worldSize.height * SIZE_UNIT_PIXELS) - size.height;

			if (cvy > max) {
				cvy = max;
			}
		}

		dv.x = cvx - sv.x;
		dv.y = cvy - sv.y;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#center()
	 */
	public void center() {
		sv.x += dv.x;
		sv.y += dv.y;

		dv.x = 0;
		dv.y = 0;

		speed = 0;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#createView(games.stendhal.client.entity.Entity)
	 */
	public Entity2DView createView(final Entity entity) {
		return (Entity2DView) Entity2DViewFactory.get().create(entity);
	}

	/*
	 * Draw the screen.
	 *
	 * @see games.stendhal.client.IGameScreen#draw()
	 */
	public void draw() {
		Collections.sort(views, entityViewComparator);

		/*
		 * Draw the GameLayers from bottom to top, relies on exact naming of the
		 * layers
		 */
		String set = gameLayers.getRPZoneLayerSet();

		Point2D pos = getViewPos();
		int xTemp = (int) pos.getX();
		int yTemp = (int) pos.getY();

		Dimension s = getViewSize();
		int w = (int) s.getWidth();
		int h = (int) s.getHeight();

		/*
		 * End of the world (map falls short of the view)?
		 */
		int xMax = Math.max(xTemp, 0);
		int yMax = Math.max(yTemp, 0);

		int xMin = Math.min(xTemp + w, worldSize.width);
		int yMin = Math.min(yTemp + h, worldSize.height);

		Point max = convertWorldToScreenView(new Point(xMax, yMax));
		Point min = convertWorldToScreenView(new Point(xMin, yMin));

		if (max.x > 0) {
			g.setColor(Color.black);
			g.fillRect(0, 0, max.x, size.height);
		}

		if (min.x < size.width) {
			g.setColor(Color.black);
			g.fillRect(min.x, 0, size.width - min.x, size.height);
		}

		if (max.y > 0) {
			g.setColor(Color.black);
			g.fillRect(0, 0, size.width, max.y);
		}

		if (min.y < size.height) {
			g.setColor(Color.black);
			g.fillRect(0, min.y, size.width, size.height - min.y);
		}

		/*
		 * Layers
		 */
		gameLayers.draw(this, set, "0_floor", xTemp, yTemp, w, h);
		gameLayers.draw(this, set, "1_terrain", xTemp, yTemp, w, h);
		gameLayers.draw(this, set, "2_object", xTemp, yTemp, w, h);
		drawEntities();
		gameLayers.draw(this, set, "3_roof", xTemp, yTemp, w, h);
		gameLayers.draw(this, set, "4_roof_add", xTemp, yTemp, w, h);
		drawTopEntities();
		drawText();

		// flush Graphics context
		g.dispose();

		/*
		 * Dialogs
		 */
//		ground.draw(g);

		/*
		 * Offline
		 */
		if (offline && (blinkOffline > 0)) {
			offlineIcon.draw(g, 560, 420);
		}

		if (blinkOffline < -10) {
			blinkOffline = 20;
		} else {
			blinkOffline--;
		}
	}

	/**
	 * Draw the screen entities.
	 */
	protected void drawEntities() {
		for (Entity2DView view : views) {
			view.draw(g);
		}
	}

	/**
	 * Draw the top portion screen entities (such as HP/title bars).
	 */
	protected void drawTopEntities() {
		for (Entity2DView view : views) {
			view.drawTop(g);
		}
	}

	/**
	 * Draw the screen text bubbles.
	 */
	protected void drawText() {
		texts.removeAll(textsToRemove);
		textsToRemove.clear();

		try {
			for (Text text : texts) {
				text.draw(this);
			}
		} catch (ConcurrentModificationException e) {
			logger.error("cannot draw text", e);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getViewPos()
	 */
	public Point2D getViewPos() {
		return new Point2D.Double(
						(double) getScreenViewPos().x / SIZE_UNIT_PIXELS,
						(double) getScreenViewPos().y / SIZE_UNIT_PIXELS);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#setMaxWorldSize(double, double)
	 */
	public void setMaxWorldSize(double width, double height) {
		worldSize = new Dimension((int) width, (int) height);

		calculateView();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#setOffline(boolean)
	 */
	public void setOffline(boolean offline) {
		this.offline = offline;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#addText(double, double,
	 *      java.lang.String, games.stendhal.client.NotificationType, boolean)
	 */
	public void addText(double x, double y, String text, NotificationType type,
			boolean isTalking) {
		addText(x, y, text, type.getColor(), isTalking);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#addText(double, double,
	 *      java.lang.String, java.awt.Color, boolean)
	 */
	public void addText(final double x, final double y, final String text,
			final Color color, final boolean talking) {
		addText(convertWorldToScreen(x), convertWorldToScreen(y), text, color,
				talking);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#addText(int, int,
	 *      java.lang.String, games.stendhal.client.NotificationType, boolean)
	 */
	public void addText(final int sx, final int sy, final String text,
			final NotificationType type, final boolean talking) {
		addText(sx, sy, text, type.getColor(), talking);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#addText(int, int,
	 *      java.lang.String, java.awt.Color, boolean)
	 */
	public void addText(int sx, int sy, final String text, final Color color, final boolean isTalking) {
		Sprite sprite = createTextBox(text, 240, color, Color.white, isTalking);

		if (isTalking) {
			// Point alignment: left, bottom
			sy -= sprite.getHeight();
		} else {
			// Point alignment: left-right centered, bottom
			sx -= (sprite.getWidth() / 2);
			sy -= sprite.getHeight();
		}

		Dimension s = getScreenSize();

		/*
		 * Try to keep the text on screen. This could mess up the "talk" origin
		 * positioning.
		 */
		if (sx < 0) {
			sx = 0;
		} else {
			int max = s.width - sprite.getWidth();

			if (sx > max) {
				sx = max;
			}
		}

		if (sy < 0) {
			sy = 0;
		} else {
			int max = s.height - sprite.getHeight();

			if (sy > max) {
				sy = max;
			}
		}

		boolean found = true;

		while (found) {
			found = false;

			for (Text item : texts) {
				if ((item.getX() == sx) && (item.getY() == sy)) {
					found = true;
					sy += (SIZE_UNIT_PIXELS / 2);
					break;
				}
			}
		}

		texts.add(new Text(sprite, sx, sy, Math.max(
				Text.STANDARD_PERSISTENCE_TIME, text.length()
						* Text.STANDARD_PERSISTENCE_TIME / 50)));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#removeText(games.stendhal.client.gui.j2d.Text)
	 */
	public void removeText(Text entity) {
		textsToRemove.add(entity);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#removeAll()
	 */
	public void removeAll() {
		views.clear();
		texts.clear();
		textsToRemove.clear();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#clear()
	 */
	public void clear() {
		g = canvas.getDrawingBuffer();

		g.setColor(Color.black);
		g.fillRect(0, 0, getScreenViewSize().width, getScreenViewSize().height);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#clearTexts()
	 */
	public void clearTexts() {
		for (Text text : texts) {
			textsToRemove.add(text);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getEntityViewAt(double, double)
	 */
	public Entity2DView getEntityViewAt(double x, double y) {
		ListIterator<Entity2DView> it;

		/*
		 * Try the physical entity areas first
		 */
		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if (view.getEntity().getArea().contains(x, y)) {
				return view;
			}
		}

		/*
		 * Now the visual entity areas
		 */
		int sx = convertWorldToScreen(x);
		int sy = convertWorldToScreen(y);

		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if (view.getArea().contains(sx, sy)) {
				return view;
			}
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getMovableEntityViewAt(double,
	 *      double)
	 */
	public Entity2DView getMovableEntityViewAt(final double x, final double y) {
		ListIterator<Entity2DView> it;

		/*
		 * Try the physical entity areas first
		 */
		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if (view.isMovable()) {
				if (view.getEntity().getArea().contains(x, y)) {
					return view;
				}
			}
		}

		/*
		 * Now the visual entity areas
		 */
		int sx = convertWorldToScreen(x);
		int sy = convertWorldToScreen(y);

		it = views.listIterator(views.size());

		while (it.hasPrevious()) {
			Entity2DView view = it.previous();

			if (view.isMovable()) {
				if (view.getArea().contains(sx, sy)) {
					return view;
				}
			}
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getTextAt(double, double)
	 */
	public Text getTextAt(double x, double y) {
		ListIterator<Text> it = texts.listIterator(texts.size());

		int sx = convertWorldToScreen(x);
		int sy = convertWorldToScreen(y);

		while (it.hasPrevious()) {
			Text text = it.previous();

			if (text.getArea().contains(sx, sy)) {
				return text;
			}
		}

		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertWorldToScreenView(Point2D)
	 */
	public Point convertWorldToScreenView(Point2D pos) {
		return convertWorldToScreenView(pos.getX(), pos.getY());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertWorldToScreenView(double,
	 *      double)
	 */
	public Point convertWorldToScreenView(double wx, double wy) {
		return new Point(convertWorldToScreen(wx) - sv.x,
				 		 convertWorldToScreen(wy) - sv.y);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertWorldToScreenView(java.awt.geom.Rectangle2D)
	 */
	public Rectangle convertWorldToScreenView(Rectangle2D wrect) {
		return convertWorldToScreenView(wrect.getX(), wrect.getY(),
				wrect.getWidth(), wrect.getHeight());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertWorldToScreenView(double,
	 *      double, double, double)
	 */
	public Rectangle convertWorldToScreenView(double wx, double wy,
			double wwidth, double wheight) {
		Point pos = convertWorldToScreenView(wx, wy);

		return new Rectangle(pos.x, pos.y,
				(int) (wwidth * SIZE_UNIT_PIXELS),
				(int) (wheight * SIZE_UNIT_PIXELS));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#isInScreen(java.awt.Rectangle)
	 */
	public boolean isInScreen(Rectangle srect) {
		return isInScreen(srect.x, srect.y, srect.width, srect.height);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#isInScreen(int, int, int, int)
	 */
	public boolean isInScreen(int sx, int sy, int swidth, int sheight) {
		return (((sx >= -swidth) && (sx < size.width)) && ((sy >= -sheight) && (sy < size.height)));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#draw(games.stendhal.client.sprite.Sprite,
	 *      double, double)
	 */
	public void draw(Sprite sprite, double wx, double wy) {
		Point p = convertWorldToScreenView(wx, wy);

		if (sprite != null) {
			int spritew = sprite.getWidth() + 2;
			int spriteh = sprite.getHeight() + 2;

			if (((p.x >= -spritew) && (p.x < size.width))
					&& ((p.y >= -spriteh) && (p.y < size.height))) {
				sprite.draw(g, p.x, p.y);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#drawInScreen(games.stendhal.client.sprite.Sprite, int, int)
	 */
	public void drawInScreen(Sprite sprite, int sx, int sy) {
		sprite.draw(g, sx, sy);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#createString(java.lang.String,
	 *      games.stendhal.client.NotificationType)
	 */
	public Sprite createString(final String text, final NotificationType type) {
		return createString(text, type.getColor());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#createString(java.lang.String,
	 *      java.awt.Color)
	 */
	public Sprite createString(String text, Color textColor) {
		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

		Image image = gc.createCompatibleImage(g.getFontMetrics().stringWidth(
				text) + 2, 16, Transparency.BITMASK);

		drawOutlineString(image.getGraphics(), textColor, text, 1, 10);

		return new ImageSprite(image);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#drawOutlineString(java.awt.Graphics,
	 *      java.awt.Color, java.lang.String, int, int)
	 */
	public void drawOutlineString(final Graphics g, final Color textColor, final String text, final int x, final int y) {
		/*
		 * Use light gray as outline for colors < 25% bright. Luminance = 0.299R +
		 * 0.587G + 0.114B
		 */
		int lum = ((textColor.getRed() * 299) + (textColor.getGreen() * 587) + (textColor.getBlue() * 114)) / 1000;

		drawOutlineString(g, textColor, (lum >= 64) ? Color.black
				: Color.lightGray, text, x, y);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#drawOutlineString(java.awt.Graphics,
	 *      java.awt.Color, java.awt.Color, java.lang.String, int, int)
	 */
	public void drawOutlineString(final Graphics g, final Color textColor,
			final Color outlineColor, final String text, final int x,
			final int y) {
		g.setColor(outlineColor);
		g.drawString(text, x - 1, y - 1);
		g.drawString(text, x - 1, y + 1);
		g.drawString(text, x + 1, y - 1);
		g.drawString(text, x + 1, y + 1);

		g.setColor(textColor);
		g.drawString(text, x, y);
	}

	private int positionStringOfSize(String text, int width) {
		String[] words = text.split("\\s+");

		int i = 1;
		// Bugfix: Prevent NPE for empty text intensifly@gmx.com
		String textUnderWidth = "";
		if (words != null) {
			textUnderWidth = words[0];
		}

		while ((i < words.length)
				&& (g.getFontMetrics().stringWidth(
						textUnderWidth + " " + words[i]) < width)) {
			textUnderWidth = textUnderWidth + " " + words[i];
			i++;
		}

		if ((textUnderWidth.length() == 0) && (words.length > 1)) {
			textUnderWidth = words[1];
		}

		if (g.getFontMetrics().stringWidth(textUnderWidth) > width) {
			return (int) ((float) width
					/ (float) g.getFontMetrics().stringWidth(textUnderWidth) * textUnderWidth.length());
		}

		return textUnderWidth.length();
	}

	// Added support formatted text displaying #keywords in another color
	// intensifly@gmx.com
	// ToDo: optimize the alghorithm, it's a little long ;)

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#formatLine(java.lang.String,
	 *      java.awt.Font, java.awt.Color)
	 */
	public AttributedString formatLine(final String line,
				final Font fontNormal, final Color colorNormal) {
		final Font specialFont = fontNormal.deriveFont(Font.ITALIC);

		try {
			// recreate the string without the # characters
			final StringBuilder temp = new StringBuilder();
			FormatTextParser parser = new FormatTextParser() {
				@Override
				public void normalText(String tok) {
					temp.append(tok + " ");
				}

				@Override
				public void colorText(String tok) {
					temp.append(tok + " ");
				}
			};
			parser.format(line);

			// create the attribute string including formating
			final AttributedString aStyledText = new AttributedString(temp.toString());

			parser = new FormatTextParser() {
				private int s = 0;

				@Override
				public void normalText(String tok) {
					aStyledText.addAttribute(TextAttribute.FONT, fontNormal, s, s + tok.length() + 1);
					aStyledText.addAttribute(TextAttribute.FOREGROUND, colorNormal, s, s + tok.length() + 1);
					s += tok.length() + 1;
				}

				@Override
				public void colorText(String tok) {
					aStyledText.addAttribute(TextAttribute.FONT, specialFont, s, s + tok.length() + 1);
					aStyledText.addAttribute(TextAttribute.FOREGROUND, Color.blue, s, s + tok.length() + 1);
					s += tok.length() + 1;
				}
			};
			parser.format(line);

			return aStyledText;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#createTextBox(java.lang.String,
	 *      int, java.awt.Color, java.awt.Color, boolean)
	 */
	public Sprite createTextBox(String text, int width, Color textColor,
			Color fillColor, boolean isTalking) {
		java.util.List<String> lines = new java.util.LinkedList<String>();

		int i = 0;
		// Added support for speech balloons. If drawn, they take 10 pixels from
		// the left. intensifly@gmx.com

		int delta = 0;

		if (fillColor != null) {
			delta = 10;
		}
		text = text.trim();
		while (text.length() > 0) {
			int pos = positionStringOfSize(text, width - delta);

			/*
			 * Hard line breaks
			 */
			int nlpos = text.indexOf('\n', 1);
			if ((nlpos != -1) && (nlpos < pos)) {
				pos = nlpos;
			}

			lines.add(text.substring(0, pos).trim());
			text = text.substring(pos);
			i++;
		}

		int numLines = lines.size();
		int lineLengthPixels = 0;

		for (String line : lines) {
			int lineWidth = g.getFontMetrics().stringWidth(line);
			if (lineWidth > lineLengthPixels) {
				lineLengthPixels = lineWidth;
			}
		}

		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

		int imageWidth = ((lineLengthPixels + delta < width) ? lineLengthPixels
				+ delta : width) + 4;
		int imageHeight = 16 * numLines;

		// Workaround for X-Windows not supporting images of height 0 pixel.
		if (imageHeight == 0) {
			imageHeight = 1;
			logger.warn("Created textbox for empty text");
		}

		Image image = gc.createCompatibleImage(imageWidth, imageHeight,
				Transparency.BITMASK);

		Graphics2D g2d = (Graphics2D) image.getGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		if (fillColor != null) {
			Composite xac = g2d.getComposite();
			AlphaComposite ac = AlphaComposite.getInstance(
					AlphaComposite.SRC_OVER, 0.8f);
			g2d.setComposite(ac);
			g2d.setColor(fillColor);
			g2d.fillRoundRect(
					10,
					0,
					((lineLengthPixels < width) ? lineLengthPixels : width) + 3,
					16 * numLines - 1, 4, 4);
			g2d.setColor(textColor);
			if (isTalking) {
				g2d.drawRoundRect(
						10,
						0,
						((lineLengthPixels < width) ? lineLengthPixels : width) + 3,
						16 * numLines - 1, 4, 4);
			} else {
				float[] dash = { 4, 2 };
				BasicStroke newStroke = new BasicStroke(2,
						BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 1, dash,
						0);
				Stroke oldStroke = g2d.getStroke();
				g2d.setStroke(newStroke);
				g2d.drawRect(
						10,
						0,
						((lineLengthPixels < width) ? lineLengthPixels : width) + 3,
						16 * numLines - 1);
				g2d.setStroke(oldStroke);
			}
			g2d.setComposite(xac);
			if (isTalking) {
				g2d.setColor(fillColor);
				Polygon p = new Polygon();
				p.addPoint(10, 3);
				p.addPoint(0, 16);
				p.addPoint(11, 12);
				g2d.fillPolygon(p);
				g2d.setColor(textColor);
				p.addPoint(0, 16);
				g2d.drawPolygon(p);
			}
		}

		i = 0;
		for (String line : lines) {
			AttributedString aStyledText = formatLine(line, g2d.getFont(), textColor);

			if (fillColor == null) {
				g2d.setColor(Color.black);
				g2d.drawString(aStyledText.getIterator(), 1, 2 + i * 16 + 9);
				g2d.drawString(aStyledText.getIterator(), 1, 2 + i * 16 + 11);
				g2d.drawString(aStyledText.getIterator(), 3, 2 + i * 16 + 9);
				g2d.drawString(aStyledText.getIterator(), 3, 2 + i * 16 + 11);
			}

			g2d.setColor(textColor);

			g2d.drawString(aStyledText.getIterator(), 2 + delta,
					2 + i * 16 + 10);
			i++;
		}

		return new ImageSprite(image);
	}

	//
	// <GameScreen2D>
	//

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertWorldToScreen(double)
	 */
	public int convertWorldToScreen(double d) {
		return (int) (d * SIZE_UNIT_PIXELS);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertWorldToScreen(double)
	 */
	public Dimension convertWorldToScreen(Dimension2D d) {
		return new Dimension((int) (d.getWidth() * SIZE_UNIT_PIXELS), (int) (d.getHeight() * SIZE_UNIT_PIXELS));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertScreenToWorld(int, int)
	 */
	public Point2D convertScreenToWorld(final int x, final int y) {
		return new Point.Double((double) x / SIZE_UNIT_PIXELS, (double) y
				/ SIZE_UNIT_PIXELS);

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertScreenViewToWorld(java.awt.Point)
	 */
	public Point2D convertScreenViewToWorld(final Point p) {
		return convertScreenViewToWorld(p.x, p.y);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#convertScreenViewToWorld(int, int)
	 */
	public Point2D convertScreenViewToWorld(final int x, final int y) {
		return convertScreenToWorld(x + getScreenViewPos().x, y + getScreenViewPos().y);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getScreenSize()
	 */
	public Dimension getScreenSize() {
		return convertWorldToScreen(worldSize);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getScreenViewSize()
	 */
	public Dimension getScreenViewSize() {
		return size;
	}


	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#getScreenViewPos()
	 */
	public Point getScreenViewPos() {
		return sv;
	}


	//
	// PositionChangeListener
	//

	/*
	 * (non-Javadoc)
	 *
	 * @see games.stendhal.client.IGameScreen#positionChanged(double, double)
	 */
	public void positionChanged(final double x, final double y) {
		int ix = (int) x;
		int iy = (int) y;

		/*
		 * Save CPU cycles
		 */
		if ((ix != center.x) || (iy != center.y)) {
			center.x = ix;
			center.y = iy;

			calculateView();
		}
	}

	//
	//

	public static class EntityViewComparator implements Comparator<Entity2DView> {
		//
		// Comparator
		//

		public int compare(Entity2DView view1, Entity2DView view2) {
			int rv;

			rv = view1.getZIndex() - view2.getZIndex();

			if (rv == 0) {
				Rectangle area1 = view1.getArea();
				Rectangle area2 = view2.getArea();

				rv = (area1.y + area1.height) - (area2.y + area2.height);

				if (rv == 0) {
					rv = area1.x - area2.x;

					/*
					 * Quick workaround to stack items in the same order they
					 * were added.
					 *
					 * TODO: Do it right on server side
					 */
					if (rv == 0) {
						rv = view1.getEntity().getID().getObjectID()
								- view2.getEntity().getID().getObjectID();
					}
				}
			}

			return rv;
		}
	}
}
