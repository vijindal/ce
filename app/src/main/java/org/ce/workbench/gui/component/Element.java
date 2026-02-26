package org.ce.workbench.gui.component;

/**
 * Data class representing a chemical element in the periodic table.
 */
public class Element {
    private final String symbol;
    private final String name;
    private final int atomicNumber;
    private final double mass;
    private final int row;
    private final int column;
    
    public Element(String symbol, String name, int atomicNumber, double mass, int row, int column) {
        this.symbol = symbol;
        this.name = name;
        this.atomicNumber = atomicNumber;
        this.mass = mass;
        this.row = row;
        this.column = column;
    }
    
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public int getAtomicNumber() { return atomicNumber; }
    public double getMass() { return mass; }
    public int getRow() { return row; }
    public int getColumn() { return column; }
    
    @Override
    public String toString() {
        return symbol + " (" + name + ")";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Element)) return false;
        Element other = (Element) obj;
        return atomicNumber == other.atomicNumber;
    }
    
    @Override
    public int hashCode() {
        return atomicNumber;
    }
}
