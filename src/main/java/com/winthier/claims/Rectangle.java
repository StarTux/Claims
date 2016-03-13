package com.winthier.claims;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

@Data
public class Rectangle {
    private final int x; // left
    private final int y; // top

    private final int dx;
    private final int dy;

    public static Rectangle forCorners(Location corner1, Location corner2) {
        return new Rectangle(Math.min(corner1.getX(), corner2.getX()),
                             Math.min(corner1.getZ(), corner2.getZ()),
                             Math.abs(corner2.getX() - corner1.getX()) + 1,
                             Math.abs(corner2.getZ() - corner1.getZ()) + 1);
    }

    public static Rectangle forNorthEastSouthWest(int north, int east, int south, int west) {
        return new Rectangle(west, north, east - west + 1, south - north + 1);
    }
    
    public boolean intersects(Rectangle other) {
        if (x + dx - 1 < other.x || x >= other.x + other.dx) return false;
        if (y + dy - 1 < other.y || y >= other.y + other.dy) return false;
        return true;
    }

    public boolean contains(int x, int y) {
        if (x < this.x || x >= this.x + this.dx) return false;
        if (y < this.y || y >= this.y + this.dy) return false;
        return true;
    }

    public boolean contains(Rectangle other) {
        if (other.x < x || other.x >= x + dx) return false;
        if (other.y < y || other.y >= y + dy) return false;
        if (other.x + other.dx -1 >= x + dx) return false;
        if (other.y + other.dy -1 >= y + dy) return false;
        return true;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", x);
        result.put("y", y);
        result.put("dx", dx);
        result.put("dy", dy);
        return result;
    }

    public static Rectangle deserialize(Map<String, Object> map) {
        int x = 0, y = 0, dx = 1, dy = 1;
        if (map.get("x") instanceof Integer) x = (Integer)map.get("x");
        if (map.get("y") instanceof Integer) y = (Integer)map.get("y");
        if (map.get("dx") instanceof Integer) dx = (Integer)map.get("dx");
        if (map.get("dy") instanceof Integer) dy = (Integer)map.get("dy");
        if (dx < 1) dx = 1;
        if (dy < 1) dy = 1;
        return new Rectangle(x, y, dx, dy);
    }

    public int getCenterX() {
        return x + (dx / 2);
    }

    public int getCenterY() {
        return y + (dy / 2);
    }

    public int getArea() {
        return dx * dy;
    }

    public int getWestBorder() { return x; }
    public int getEastBorder() { return x + dx - 1; }
    public int getNorthBorder() { return y; }
    public int getSouthBorder() { return y + dy - 1; }

    public int getWidth() { return dx; }
    public int getHeight() { return dy; }

    public Rectangle expand(int amount, CardinalDirection direction) {
        int x = this.x;
        int y = this.y;
        int dx = this.dx;
        int dy = this.dy;
        switch (direction) {
        case NORTH:
            y -= amount;
        case SOUTH:
            dy += amount;
            break;
        case WEST:
            x -= amount;
        case EAST:
            dx += amount;
            break;
        }
        return new Rectangle(x, y, dx, dy);
    }

    public Rectangle outset(int amount) {
        int x = this.x - amount;
        int y = this.y - amount;
        int dx = this.dx + amount + amount;
        int dy = this.dy + amount + amount;
        return new Rectangle(x, y, dx, dy);
    }

    public Rectangle inset(int amount) {
        int x = this.x + amount;
        int y = this.y + amount;
        int dx = Math.max(1, this.dx - amount - amount);
        int dy = Math.max(1, this.dy - amount - amount);
        return new Rectangle(x, y, dx, dy);
    }

    @Override
    public String toString() {
        return String.format("Rectangle(%d,%d,%d,%d)", x, y, dx, dy);
    }
}
