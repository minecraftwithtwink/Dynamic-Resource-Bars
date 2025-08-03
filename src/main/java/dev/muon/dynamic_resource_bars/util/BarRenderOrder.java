package dev.muon.dynamic_resource_bars.util;

/**
 * Defines the render order for bar groups.
 * Bars with lower ordinal values render first (behind other bars).
 * Bars with higher ordinal values render last (on top of other bars).
 */
public enum BarRenderOrder {
    /**
     * Renders first (behind all other bars)
     */
    BACKGROUND(0),
    
    /**
     * Renders after background bars
     */
    MANA(1),
    
    /**
     * Renders after mana bars
     */
    STAMINA(2),
    
    /**
     * Renders after stamina bars
     */
    HEALTH(3),
    
    /**
     * Renders after health bars
     */
    ARMOR(4),
    
    /**
     * Renders after armor bars
     */
    AIR(5),
    
    /**
     * Renders last (on top of all other bars)
     */
    FOREGROUND(6);
    
    private final int order;
    
    BarRenderOrder(int order) {
        this.order = order;
    }
    
    /**
     * Gets the render order value.
     * Lower values render first (behind), higher values render last (on top).
     */
    public int getOrder() {
        return order;
    }
    
    /**
     * Compares this render order with another.
     * @param other The other render order to compare with
     * @return true if this should render before the other
     */
    public boolean rendersBefore(BarRenderOrder other) {
        return this.order < other.order;
    }
    
    /**
     * Compares this render order with another.
     * @param other The other render order to compare with
     * @return true if this should render after the other
     */
    public boolean rendersAfter(BarRenderOrder other) {
        return this.order > other.order;
    }
} 