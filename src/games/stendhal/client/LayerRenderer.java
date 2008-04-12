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

//
//

import games.stendhal.client.sprite.Tileset;

import java.awt.Dimension;
import java.awt.geom.Point2D;

/**
 * This is a helper base class to render a layer.
 */
public abstract class LayerRenderer {

	protected int width;

	protected int height;

	public LayerRenderer() {
		width = 0;
		height = 0;
	}

	/** @return the width in world units */
	public int getWidth() {
		return width;
	}

	/** @return the height in world units */
	public int getHeight() {
		return height;
	}

	/**
	 * Render the layer to screen. We assume that game screen will clip.
	 * 
	 * @param screen
	 *            The screen to draw on.
	 */
	public void draw(IGameScreen screen) {
		Point2D pos = screen.getViewPos();
		Dimension s = screen.getViewSize();

		draw(screen, (int) pos.getX(), (int) pos.getY(), s.width, s.height);
	}

	/**
	 * Render the layer to screen. We assume that game screen will clip.
	 * 
	 * @param screen
	 *            The screen to draw on.
	 * @param x
	 *            The view X world coordinate.
	 * @param y
	 *            The view Y world coordinate.
	 * @param w
	 *            The view world width.
	 * @param h
	 *            The view world height.
	 */
	public abstract void draw(IGameScreen screen, int x, int y, int w, int h);

	// TODO: Cleanly remove from this level
	public abstract void setTileset(Tileset tileset);
}
