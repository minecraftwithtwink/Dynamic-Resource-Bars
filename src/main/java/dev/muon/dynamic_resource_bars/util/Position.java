package dev.muon.dynamic_resource_bars.util;

public final class Position {
    private final int x;
    private final int y;
    
    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public int x() {
        return x;
    }
    
    public int y() {
        return y;
    }
    
    public Position offset(int x, int y) {
        return new Position(this.x + x, this.y + y);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Position that = (Position) obj;
        return x == that.x && y == that.y;
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(x, y);
    }
    
    @Override
    public String toString() {
        return "Position[x=" + x + ", y=" + y + "]";
    }
}