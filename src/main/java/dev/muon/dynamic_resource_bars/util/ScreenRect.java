package dev.muon.dynamic_resource_bars.util;
 
public final class ScreenRect {
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    
    public ScreenRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }
    
    public int x() {
        return x;
    }
    
    public int y() {
        return y;
    }
    
    public int width() {
        return width;
    }
    
    public int height() {
        return height;
    }
    
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ScreenRect that = (ScreenRect) obj;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y, width, height);
    }
    
    @Override
    public String toString() {
        return "ScreenRect[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]";
    }
} 