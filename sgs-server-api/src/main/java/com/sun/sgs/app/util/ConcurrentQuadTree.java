/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.app.util;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.sun.sgs.app.ObjectNotFoundException;

/**
 * The {@code ConcurrentQuadTree} is a data structure which organizes
 * Cartesian coordinate pairs (or {@code Point}s) in a two-dimensional
 * rectangular region. More specifically, the data structure divides existing
 * regions into smaller regions in order to allot one region for each
 * {@code Point}. The notion of a quadtree means that each subdivision
 * results in an existing region dividing into four smaller, equally-sized
 * regions. Therefore, each {@code Point} inserted into the
 * {@code ConcurrentQuadTree} will have a region it and only it resides in. A
 * quadtree enables a two-dimensional space to efficiently hold onto a certain
 * number of {@code Point}s using a relatively simple and low-cost scheme.
 * <p>
 * This type of organization is best interpreted as a tree whereby upper level
 * nodes correspond to regions which contain smaller regions. This means that
 * the regions that contain {@code Point}s are leaf nodes. The depth of leaf
 * nodes depends on the maximum depth of the tree provided during
 * instantiation: a large depth limit allows for more entries and for them to
 * exist within very small regions, whereas a smaller depth limit can support
 * fewer entries but they exist in larger regions. The depth limit should be
 * proportional to the number of entries which need to be stored and the
 * minimum region size you wish to support.
 * <p>
 * The {@code ConcurrentQuadTree} is backed with a {@code ConcurrentHashMap}
 * instance so that some inline operations can occur with better performance.
 * These operations include lookups in the tree for an add, remove, set, and
 * get methods. Overall, many of the methods occur in logarithmic time because
 * the tree has to be walked in order to locate the correct region to
 * manipulate. This is not often very costly because the quadtree has a
 * tendency to grow horizontally, especially if values are spaced far enough
 * apart and if the tree has a shallow depth limit. If no depth limit is
 * explicitly set, the default value is 5.
 * <p>
 * To allow for concurrency, this data structure does not propagate changes
 * from the leaf node to the root element. Instead, the propagation stops at
 * the first level so that operations can simultaneously take place in any of
 * the four quadrants of the root without contention. Since the tree does not
 * grow upwards, nodes that have been created maintain their tree depth
 * permanently until they are removed. A tree depth of 0 corresponds to the
 * depth of the root node, with each subsequent level incrementing the depth.
 * Nodes are removed when there are no children containing inserted values.
 * This measure is taken to improve the performance of walking the tree and
 * reduce memory requirements.
 * <p>
 * Iteration of the tree is achieved by assembling a {@code Set} of the
 * entries in the {@code ConcurrentHashMap} and iterating through them.
 * Therefore, the order of elements in the iteration is not necessarily the
 * same for each iterator constructed.
 * 
 * @param <T> the type the quadtree is to hold
 */
public class ConcurrentQuadTree<T> {

    public static void main(String[] args) {
	ConcurrentQuadTree<String> tree = new ConcurrentQuadTree<String>(3, 1, 0, 0, 10, 10);
	tree.add(1, 1, "A");
	tree.add(4, 4, "B");
	tree.add(7, 7, "C");
	tree.add(7, 7, "C");
	Iterator<String> iter = tree.iterator();
	int i=0;
	while (iter.hasNext()) {
	    if (++i == 2){
		iter.remove();
	    }
	    iter.next();
	}
	iter = tree.iterator();
	while (iter.hasNext()) {
	    System.out.println(iter.next());
	}
	
    }
    
    /**
     * An enumeration corresponding to fields and methods pertaining to
     * Cartesian coordinates. These are useful for when pairs of coordinates
     * need to be organized in an array for operations sensitive to positional
     * information
     */
    public static enum Coordinate {
	X_MIN, // Minimum x-value of the pair of coordinates
	X_MAX, // Maximum x-value of the pair of coordinates
	Y_MIN, // Minimum y-value of the pair of coordinates
	Y_MAX; // Maximum y-value of the pair of coordinates

	/*
	 * Integer equivalents for the Coordinate enumeration. It is important
	 * for these values to be consecutive integers starting at 0 so that
	 * they can be used as indices for any arrays harnessing pairs of
	 * coordinates like the Envelope class
	 */
	static final int iX_MIN = 0;
	static final int iX_MAX = 1;
	static final int iY_MIN = 2;
	static final int iY_MAX = 3;

	/**
	 * Converts a {@code Coordinate} into its integer equivalent
	 * 
	 * @param coord the {@code Coordinate} to convert
	 * @return the integer equivalent of the {@code Coordinate}
	 */
	public static int toInt(Coordinate coord) {
	    switch (coord) {
		case X_MIN:
		    return iX_MIN;
		case X_MAX:
		    return iX_MAX;
		case Y_MIN:
		    return iY_MIN;
		case Y_MAX:
		    return iY_MAX;
		default:
		    return -1;
	    }
	}
    }

    /**
     * Default maximum depth of a quadtree. A depth of 5 should provide a
     * fairly granular unit of measurement which should suffice for most
     * implementations
     */
    private static final int DEFAULT_MAX_DEPTH = 5;

    /** The maximum allowed depth of the tree */
    private final int maxDepth;

    /**
     * An object consisting of two corners that comprise the box representing
     * the sample space
     */
    private final Envelope envelope;

    /** The root element of the quadtree */
    private Node<T> root;


    /**
     * Five-argument constructor which defines a maximum bucket size and
     * a pair of x,y coordinates denoting the bounding envelope. The
     * maximum tree depth is set to {@code DEFAULT_MAX_DEPTH} = 5.
     * @param bucketSize the maximum capacity of a leaf node
     * @param x1 the x-coordinate of the first point defining the tree's
     * envelope
     * @param y1 the y-coordinate of the first point defining the tree's
     * envelope
     * @param x2 the x-coordinate of the second point defining the tree's
     * envelope
     * @param y2 the x-coordinate of the second point defining the tree's
     * envelope
     */
    public ConcurrentQuadTree(int bucketSize, double x1, double y1, double x2,
	    double y2) {
	this(DEFAULT_MAX_DEPTH, bucketSize, x1, y1, x2, y2);
    }
    
    /**
     * The six-argument constructor which defines a quadtree with a depth
     * supplied as a parameter. The area corresponding to this instance is
     * defined by the supplied coordinates whereby ({@code x1}, {@code y1})
     * represent the first {@code Point} and ({@code x2}, {@code y2})
     * represents the second {@code Point} of the defining {@code Envelope}.
     * 
     * @param maxDepth the maximum depth the tree is permitted to grow; this
     * value cannot be negative
     * @param bucketSize the maximum capacity of a leaf node
     * @param x1 the x-coordinate of the first point defining the tree's
     * envelope
     * @param y1 the y-coordinate of the first point defining the tree's
     * envelope
     * @param x2 the x-coordinate of the second point defining the tree's
     * envelope
     * @param y2 the x-coordinate of the second point defining the tree's
     * envelope
     */
    public ConcurrentQuadTree(int maxDepth, int bucketSize, double x1, double y1, double x2,
	    double y2) {
	if (maxDepth < 0) {
	    throw new IllegalArgumentException(
		    "Maximum depth cannot be negative");
	}
	this.maxDepth = maxDepth;
	envelope = new Envelope(new Point(x1, y1), new Point(x2, y2));
	root = new Node<T>(envelope, maxDepth, bucketSize);
    }

    /**
     * Determines if the tree is empty.
     * 
     * @return {@code true} if the tree is empty, and {@code false} otherwise
     */
    public boolean isEmpty() {
	return (root.children == null) && (root.values == null);
    }

    /**
     * Adds the element to the quadtree given the coordinate values. The
     * element will be added as long as a vacant region exists in the quadtree
     * at the specified location, or if the tree is permitted to grow deeper.
     * 
     * @param x the x-coordinate of the element
     * @param y the y-coordinate of the element
     * @param element the element to store
     * @return {@code true} if the element was added, and {@code false}
     * otherwise
     * @throws IllegalArgumentException if the coordinates are not contained
     * within the envelope defined by the quadtree
     */
    public boolean add(double x, double y, T element) {
	// Check to see that the node is within bounds since
	// the returned quadrant could be null if the point is
	// out of bounds
	Point point = new Point(x, y);
	Object quadrant = Node.Quadrant.determineQuadrant(envelope, point);
	if (!(quadrant instanceof Node.Quadrant)) {
	    throw new IllegalArgumentException(
		    "The coordinates are not contained within the envelope");
	}

	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.add(point, element, true);
    }

    

    /**
     * Removes an element from the quadtree corresponding to the provided
     * coordinate.
     * 
     * @param x the x-coordinate of the element to remove
     * @param y the y-coordinate of the element to remove
     * @return the object corresponding to the coordinate, or {@code null} if
     * none exists
     */
    public T remove(double x, double y) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.remove(point);
    }


    /**
     * Replaces the element at the given coordinate with the given parameter.
     * 
     * @param x the x-coordinate to set the new element
     * @param y the y-coordinate to set the new element
     * @param element the new element to replace the current one
     * @return the old element which was replaced, or {@code null} if there
     * was no element existing at the supplied coordinate
     */
    public T set(double x, double y, T element) {
	Point point = new Point(x, y);
	Node<T> leaf = Node.getLeafNode(root, point);
	return leaf.setValue(point, element);
    }


    /**
     * Returns the number of elements stored within this data structure
     * 
     * @return the number of elements stored within this data structure
     */
    public int size() {
	return root.size();
    }

    /**
     * Returns the element with the given Cartesian parameters. If the
     * paramters do not correspond to a stored element, then {@code null} is
     * returned.
     * 
     * @param x the x-coordinate of the arbitrary point
     * @param y the y-coordinate of the arbitrary point
     * @return the element at the given coordinates, or {@code null} if none
     * exists
     */
    public T get(double x, double y) {
	Point point = new Point(x, y);
	Node<T> node = Node.getLeafNode(root, point);
	Entry<T> entry = node.get(point);
	return (entry == null ? null : entry.getValue());
    }

    
    /**
     * Returns the integer coordinate represented by the bound supplied as the
     * parameter. In other words, if the call {@code getEnvelopeBound(X_MIN)}
     * is made on the quadtree whose envelope consists of the corner
     * coordinate pair of {@code (0,10) & (90, 100)}, then the value returned
     * is {@code 0}; the minimum x-coordinate of the two corners.
     * 
     * @param direction the direction of interest for the envelope
     * @return a double value representing the envelope border for the given
     * direction, or {@code NaN} if the direction is invalid
     */
    public double getDirectionalEnvelopeBound(Coordinate direction) {
	double[] coords = Envelope.organizeCoordinates(this.envelope);

	switch (direction) {
	    case X_MAX:
		return coords[Coordinate.toInt(Coordinate.X_MAX)];
	    case X_MIN:
		return coords[Coordinate.toInt(Coordinate.Y_MIN)];
	    case Y_MAX:
		return coords[Coordinate.toInt(Coordinate.Y_MAX)];
	    case Y_MIN:
		return coords[Coordinate.toInt(Coordinate.Y_MIN)];
	    default:
		return Double.NaN;
	}
    }

  

    /**
     * Returns an iterator for the elements which are contained within the the
     * envelope created by the two given coordinates. The elements which can
     * be iterated are in no particular order, and there may not be any
     * elements to iterate over
     * 
     * @param x1 the x-coordinate of the first corner
     * @param y1 the y-coordinate of the first corner
     * @param x2 the x-coordinate of the second corner
     * @param y2 the y-coordinate of the second corner
     * @return an iterator which can traverse over the entries within the
     * coordinates representing the envelope
     */
    public Iterator<T> envelopeIterator(double x1, double y1, double x2, double y2) {
	Point corner1 = new Point(x1, y1);
	Point corner2 = new Point(x2, y2);
	Envelope envelope = new Envelope(corner1, corner2);
	
	return new ElementIterator<T>(root, envelope);
    }

    

    /**
     * Asynchronously clears the tree and replaces it with an empty
     * implementation.
     */
    public void clear() {

    }

    /**
     * Returns an iterator over the elements contained in the
     * {@code backingMap}. The {@code backingMap} corresponds to all the
     * elements in the tree.
     * 
     * @return an {@code Iterator} over all the elements in the map
     */
    public Iterator<T> iterator() {
	return new ElementIterator<T>(root, envelope);
    }

    // ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
    // ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

    /**
     * An iterator which walks through the entries stored in the quadtree.
     * This implementation allows for serialization, while also reporting
     * consistency problems in the form of a
     * {@code ConcurrentModificationException}. Data integrity checks are
     * performed at the beginning of each iterator operation to ensure that
     * the data integrity is consistent. If changes have occurred, such as a
     * node removed while the iterator was serialized, then a
     * {@code ConcurrentModificationException} is thrown.
     */
    static class ElementIterator<T> implements Iterator<T>, Serializable {
	private static final long serialVersionUID = 1L;
	private final Envelope envelope;
	private int dataIntegrityValue;
	private boolean canRemove;
	private Node<T> current;
	private Entry<T> entry;
	private Entry<T> next;
	private Iterator<Entry<T>> entryIterator;
	private boolean isFullyContained;

	
	ElementIterator(Node<T> root) {
	    this(root, null);
	}
	
	ElementIterator(Node<T> root, Envelope envelope) {
	    this.envelope = envelope;
	    canRemove = false;
	    current = getFirstLeafNode(root);
	    dataIntegrityValue = current.getDataIntegrityValue();
	    entryIterator = current.getValues().iterator();
	    next = prepareNextElement();
	    isFullyContained = false;
	    entry = null;
	}

	/**
	 * Checks whether the node has been modified while the iterator was
	 * serialized. If so, a {@code ConcurrentModificationException} is
	 * thrown
	 * 
	 * @throws ConcurrentModificationException
	 */
	private void checkDataIntegrity()
		throws ConcurrentModificationException {
	    try {
		if (current.getDataIntegrityValue() == dataIntegrityValue) {
		    return;
		}
	    } catch (ObjectNotFoundException onfe) {
	    }
	    throw new ConcurrentModificationException(
		    "The Node has been modified or removed");
	}

	/**
	 * Returns <tt>true</tt> if the iteration has more elements. (In
	 * other words, returns <tt>true</tt> if <tt>next</tt> would
	 * return an element rather than throwing an exception.)
	 * 
	 * @return <tt>true</tt> if the iterator has more elements.
	 */
	public boolean hasNext() {
	    return (next != null);
	}

	
	private Entry<T> prepareNextElement() {
	    // Try to find the next qualified entry in the current list
	    Entry<T> anEntry = iterateToNextQualifiedElement();
	    if (anEntry != null) {
		return anEntry;
	    }
	    
	    /*
	     * If we didn't find an entry (it was null), locate the next node
	     * which is at least partially contained by the iterator's
	     * envelope and iterate through it. If it has no valid entries,
	     * keep searching until we run out of nodes.
	     */
	    current = getNextNodePossiblyContainingEntries(current);
	    while (current != null) {
		entryIterator = current.getValues().iterator();
		anEntry = iterateToNextQualifiedElement();
		if (anEntry != null) {
		    return anEntry;
		}
		current = getNextNodePossiblyContainingEntries(current);
	    }
	    return null;
	}
	
	
	private Entry<T> iterateToNextQualifiedElement() {
	    Entry<T> ent;
	    
	    while (entryIterator.hasNext()) {
		ent = entryIterator.next();
		if (isFullyContained || envelope.contains(ent.coordinate)) {
		    return ent;
		}
	    }
	    return null;
	}
	
	
	/**
	 * Returns the next element in the iteration.
	 * 
	 * @return the next element in the iteration.
	 * @exception NoSuchElementException iteration has no more elements.
	 * @throws ConcurrentModificationException if the next node in the
	 * sequence was removed, or after deserialization, if this node was
	 * modified or removed.
	 */
	public T next() {
	    checkDataIntegrity();
	    if (!hasNext()) {
		throw new NoSuchElementException();
	    }

	    // since we called next(), we are now allowed to call
	    // a subsequent Iterator.remove()
	    canRemove = true;

	    // fetch the next element
	    entry = next;
	    next = prepareNextElement();
	    return entry.getValue();
	}
	
	

	/**
	 * Retrieves the first non-null leaf node in a tree, rooted by
	 * {@code node}, which is either fully contained or partially
	 * contained by the defined envelope. If there is no node that has a
	 * value in the tree, then {@code null} is returned.
	 * 
	 * @param node the root of the tree or subtree
	 * @return the first child with a non-{@code null} value, or
	 * {@code null} if the child is a leaf but is null
	 * @throws IllegalStateException if no leaf node with a non-null value
	 * exists
	 */
	private Node<T> getFirstLeafNode(Node<T> node) {
	    // If the given node is a leaf with values, we are done
	    if (node.isLeaf()) {
		if (node.getValues() != null &&
			envelope.getContainment(node.getEnvelope()) != 
			    Envelope.Containment.NONE) {
		    return node;
		} else {
		    return null;
		}
	    }

	    // Iterate through all the children in a depth-first
	    // search looking for the first encountered leaf
	    for (int i = 0; i < node.numChildren; i++) {
		Node<T> child = node.getChild(i);
		Node<T> leaf = getFirstLeafNode(child);
		if (leaf != null) {
		    return leaf;
		}
	    }

	    return null;
	    /*
	    // We shouldn't get here: every subtree must have at least
	    // one leaf with a value, otherwise the subtree should not exist.
	    throw new IllegalStateException(
		    "The subtree does not have a leaf "
			    + "with a populated value");
			    */
	}

	
	/**
	 * Given the current node, return the next node in succession
	 * (using a depth-first search) which has entries
	 * @param current the current node being examined
	 * @return the next node containing entries
	 */
	private Node<T> getNextNodePossiblyContainingEntries(Node<T> current) {
	    Node<T> parent = current.getParent();
	    
	    // End condition: we reached the root; there is no next leaf node
	    if (parent == null) {
		return null;
	    }

	    // Try and fetch siblings. If they are not leaves, then
	    // try to retrieve their children
	    Node.Quadrant quadrant =
		    Node.Quadrant.next(current.getQuadrant());
	    while (quadrant != null) {
		Node<T> child = parent.getChild(quadrant);
		
		// Skip over nodes not contained by a defined envelope
		if (envelope != null &&
			envelope.getContainment(child.getEnvelope()) == 
			    Envelope.Containment.NONE) {
		    continue;
		}
		
		// Dig deeper if child is not a leaf,
		// or if it is a leaf with stored entries,
		// return it. Otherwise, keep searching this level.
		if (!child.isLeaf()) {
		    return getFirstLeafNode(child);

		} else if (child.getValues() != null) {
		    return child;

		}
		// continue checking successive siblings
		quadrant = Node.Quadrant.next(quadrant);
	    }
	    // propagate towards the root
	    return getNextNodePossiblyContainingEntries(parent);
	}

	/**
	 * Removes from the underlying collection the last element returned by
	 * the iterator (optional operation). This method can be called only
	 * once per call to <tt>next</tt>. The behavior of an iterator is
	 * unspecified if the underlying collection is modified while the
	 * iteration is in progress in any way other than by calling this
	 * method.
	 * 
	 * @exception IllegalStateException if the <tt>next</tt> method has
	 * not yet been called, or the <tt>remove</tt> method has already
	 * been called after the last call to the <tt>next</tt> method.
	 */
	public void remove() {
	    checkDataIntegrity();

	    // We can only remove if we have previously called next()
	    if (!canRemove) {
		throw new IllegalStateException(
			"Remove needs to follow Iterator.next()");
	    }

	    canRemove = false;
	    current.remove(entry.coordinate);
	    dataIntegrityValue = current.getDataIntegrityValue();
	}

    }

    /**
     * A region, defined by two {@code Points}, which represents the area
     * belonging to a certain object. The two {@code Point}s representing
     * the envelope are Cartesian points which correspond to corner points of
     * an imaginary box. Each x and y coordinate for both points represent the
     * bounds of this box, and therefore, the bounds of the {@code Envelope}.
     * For simplicity, the {@code Envelope}'s edges are only allowed to be
     * parallel or perpendicular to the Cartesian axes, meaning the
     * {@code Envelope} edges either intersect the axes at right angles or
     * coincide with them.
     */
    static class Envelope {
	
	/**
	 * Specifies the degree of containment of another object, usually
	 * a {@code Point} or another {@code Envelope}.
	 */
	static enum Containment {
	    NONE,	// no containment in the envelope
	    PARTIAL,	// partial containment
	    FULL;	// full containment
	}
	
	/** An array of two points to represent the envelope area */
	final Point[] bounds;

	public String toString() {
	    StringBuilder sb = new StringBuilder();
	    sb.append("<(");
	    sb.append(bounds[0].x);
	    sb.append(", ");
	    sb.append(bounds[0].y);
	    sb.append(") ");
	    sb.append("(");
	    sb.append(bounds[1].x);
	    sb.append(", ");
	    sb.append(bounds[1].y);
	    sb.append(")>");
	    return sb.toString();
	}
	
	/**
	 * Constructs a new {@code Envelope} given two points representing
	 * diagonal corners
	 * 
	 * @param a one of the corners of the {@code Envelope}
	 * @param b the other (diagonal) corner of the {@code Envelope}
	 */
	Envelope(Point a, Point b) {
	    bounds = new Point[] { a, b };
	}

	/**
	 * Creates the bounds given the parent's bounds and our intended
	 * quadrant
	 * 
	 * @param parentEnvelope the parent's envelope
	 * @param quadrant the quadrant to determine
	 * @return the bounds for this node
	 */
	static Envelope createBounds(Envelope parentEnvelope,
		Node.Quadrant quadrant) {
	    // get the individual coordinates
	    double[] coords = organizeCoordinates(parentEnvelope);

	    // Create the middle of the region, which is guaranteed to be a
	    // corner of the node's bounds. Time to find the other corner
	    Point middle = calculateMiddle(parentEnvelope);

	    Point corner;
	    switch (quadrant) {
		case NW:
		    corner =
			    new Point(coords[Coordinate.iX_MIN],
				    coords[Coordinate.iY_MAX]);
		    break;
		case NE:
		    corner =
			    new Point(coords[Coordinate.iX_MAX],
				    coords[Coordinate.iY_MAX]);
		    break;
		case SW:
		    corner =
			    new Point(coords[Coordinate.iX_MIN],
				    coords[Coordinate.iY_MIN]);
		    break;
		case SE:
		default:
		    corner =
			    new Point(coords[Coordinate.iX_MAX],
				    coords[Coordinate.iY_MIN]);
	    }
	    return new Envelope(middle, corner);
	}

	/**
	 * Organizes the coordinates into an array so that the minimum and
	 * maximum values can be obtained easily. The array's values are best
	 * accessed using the fields {@code X_MIN}, {@code X_MAX},
	 * {@code Y_MIN}, or {@code Y_MAX} as array indices.
	 * 
	 * @param envelope the region, represented as an array of two
	 * {@code Points}
	 * @return an array which contains individual coordinates
	 */
	private static double[] organizeCoordinates(Envelope envelope) {
	    Point[] bounds = envelope.bounds;

	    double xMin = Math.min(bounds[0].x, bounds[1].x);
	    double yMin = Math.min(bounds[0].y, bounds[1].y);
	    double xMax = Math.max(bounds[0].x, bounds[1].x);
	    double yMax = Math.max(bounds[0].y, bounds[1].y);

	    double[] values = new double[4];
	    values[Coordinate.iX_MIN] = xMin;
	    values[Coordinate.iX_MAX] = xMax;
	    values[Coordinate.iY_MIN] = yMin;
	    values[Coordinate.iY_MAX] = yMax;
	    return values;
	}

	/**
	 * Checks to see if this {@code Envelope} instance contains the
	 * {@code Envelope} supplied as the parameter. Containment is
	 * {@code true} if the argument is either fully contained or if the
	 * {@code Envelope}s share common boundaries.
	 * 
	 * @param anotherEnvelope the {@code Envelope} for which to check
	 * containment
	 * @return {@code Containment.FULL} if all corners are contained in
	 * the envelope (inclusive), {@code Containment.PARTIAL} if some
	 * corners are contained but not all, and {@code Containment.NONE} if
	 * none are contained
	 */
	Containment getContainment(Envelope anotherEnvelope) {
	    double[] coords = organizeCoordinates(this);
	    double[] arg = organizeCoordinates(anotherEnvelope);
	    
	    // Increment every time we have a coordinate contained in the bounds
	    // of "this" envelope
	    byte totalX = 0;
	    byte totalY = 0;
	    totalX += (coords[Coordinate.iX_MIN] <= arg[Coordinate.iX_MIN] ? 1 : 0);
	    totalX += (coords[Coordinate.iX_MAX] >= arg[Coordinate.iX_MAX] ? 1 : 0);
	    totalY += (coords[Coordinate.iY_MIN] <= arg[Coordinate.iY_MIN] ? 1 : 0);
	    totalY += (coords[Coordinate.iY_MAX] >= arg[Coordinate.iY_MAX] ? 1 : 0);
	    
	    /*
	     * For total containment, all points should be contained. For
	     * partial containment, at least one X and Y coordinate need to be
	     * contained; hence both totals should be larger than 0.
	     * Otherwise, there is no containment.
	     */
	    if (totalX == 2 && totalY == 2) {
		return Containment.FULL;
	    } else if (totalX > 0 && totalY > 0) {
		return Containment.PARTIAL;
	    } else {
		return Containment.NONE;
	    }
	}

	/**
	 * Checks to see if this {@code Envelope} instance contains the
	 * {@code Point} supplied as the parameter.
	 * 
	 * @param point the {@code Point} for which to check containment
	 * @return {@code true} if the {@code Point} lies within or on the
	 * {@code Envelope} border, and {@code false} otherwise
	 */
	boolean contains(Point point) {
	    // Since a point cannot be partially contained, if it is not
	    // contained, return false; otherwise return true;
	    if (getContainment(new Envelope(point, point)) == Containment.NONE) {
		return false;
	    }
	    return true;
	}

	/**
	 * Returns a Point representing the middle of the region
	 * 
	 * @param envelope the corner points specifying the region to find the
	 * middle of
	 * @return the {@code Point} representing the middle
	 */
	private static Point calculateMiddle(Envelope envelope) {
	    double[] coords = organizeCoordinates(envelope);
	    return new Point(
		    coords[Coordinate.iX_MIN] +
			    ((coords[Coordinate.iX_MAX] - coords[Coordinate.iX_MIN]) / 2),
		    coords[Coordinate.iY_MIN] +
			    ((coords[Coordinate.iY_MAX] - coords[Coordinate.iY_MIN]) / 2));
	}
    }

    /**
     * Represents an entry in the quadtree by maintaining a reference to the
     * stored object and its coordinates in the form of a {@code Point}
     * object.
     */
    static class Entry<T> {
	/** The coordinate of the element */
	final Point coordinate;

	/** The value of the element */
	private T value;

	Entry(Point coord, T value) {
	    assert (value != null) : "Value cannot be null";
	    coordinate = coord;
	    this.value = value;
	}
	
	/**
	 * Sets the value of the entry, in the event it
	 * is changed during a {@code set()} operation.
	 * @param value the new value, which must not be
	 * {@code null}
	 */
	void setValue(T value) {
	    assert (value != null) : "Value cannot be null";
	    this.value = value;
	}
	
	/**
	 * Returns the value of the entry which cannot be {@code null}.
	 * @return the value of the entry
	 */
	T getValue() {
	    return value;
	}
	
	public String toString(){
	    return value.toString();
	}
    }

    /**
     * A class that represents a point as an ({@code x}, {@code y})
     * coordinate pair.
     */
    public static class Point {
	/** the format for rounded doubles; provides eight decimal spaces */
	private static final String DEFAULT_DECIMAL_FORMAT = "0.########";

	/** the x-coordinate */
	final double x;

	/** the y-coordinate */
	final double y;

	/**
	 * Constructor which creates a new {@code Point} instance given an x
	 * and y-coordinate. The constructor will appropriately round the
	 * parameters if they exceed three decimal spaces.
	 * 
	 * @param x x-coordinate of the point
	 * @param y y-coordinate of the point
	 */
	Point(double x, double y) {
	    this.x = round(x);
	    this.y = round(y);
	}
	
	/**
	 * Returns the x-coordinate
	 * @return the x-coordinate
	 */
	public double getX() {
	    return x;
	}
	
	/**
	 * Returns the y-coordinate
	 * @return the y-coordinate
	 */
	public double getY() {
	    return y;
	}

	/**
	 * Rounds the parameter to three decimal spaces. If the first
	 * non-significant digit is 5 or greater, then the least-significant
	 * bit is rounded up. Otherwise, it is left unchanged. For example,
	 * the values 2.3455, 2.3456, 2.3457, 2.3458, and 2.3459 all round to
	 * 2.346, while the values 2.3450, 2.3451, 2.3452, 2.3453, and 2.3454
	 * all truncate to 2.345.
	 * 
	 * @param value the value to round
	 * @return the rounded value, to three decimal spaces
	 */
	private static double round(double value) {
	    DecimalFormat df = new DecimalFormat(DEFAULT_DECIMAL_FORMAT);
	    return new Double(df.format(value)).doubleValue();
	}

	/**
	 * Determines if an object is equal to {@code this}. The method will
	 * check that it is an instance of the {@code Point} class and check
	 * that the coordinates are identical.
	 * 
	 * @param obj object to check equality to this
	 * @return {@code true} if the parameter is equal to this, and
	 * {@code false} otherwise
	 */
	public boolean equals(Object obj) {
	    // If it is not an instance, it is not equal
	    if (!(obj instanceof Point)) {
		return false;
	    }
	    Point param = (Point) obj;
	    return (this.x == param.x) && (this.y == param.y);
	}
	
	
	public String toString() {
	    StringBuilder sb = new StringBuilder();
	    sb.append("(");
	    sb.append(x);
	    sb.append(", ");
	    sb.append(y);
	    sb.append(")");
	    return sb.toString();
	}
    }

    /**
     * A class that represents a general-purpose node in the
     * {@code ConcurrentTree}. This class can either represent an
     * intermediate node or a leaf node
     * 
     * @param <T> the type of coordinates to store
     */
    static class Node<T> {
	/** Enumeration representing the different quadrants for each node */
	public static enum Quadrant {
	    NW, // Top-left corner
	    NE, // Top-right corner
	    SW, // Bottom-left corner
	    SE; // Bottom-right corner

	    // Integer counterparts
	    private static final int iNW = 0;
	    private static final int iNE = 1;
	    private static final int iSW = 2;
	    private static final int iSE = 3;

	    /**
	     * Converts a {@code Quadrant} into an integer representation
	     * 
	     * @param quadrant the value to convert to an integer
	     * @return an integer representing the {@code Quadrant}
	     */
	    public static int toInt(Quadrant quadrant) {
		switch (quadrant) {
		    case NW:
			return iNW;
		    case NE:
			return iNE;
		    case SW:
			return iSW;
		    case SE:
			return iSE;
		    default:
			return -1;
		}
	    }

	    /**
	     * Converts an integer into a {@code Quadrant}
	     * 
	     * @param value the value to convert
	     * @return the corresponding {@code Quadrant}
	     */
	    public static Quadrant toQuadrant(int value) {
		switch (value) {
		    case iNW:
			return NW;
		    case iNE:
			return NE;
		    case iSW:
			return SW;
		    case iSE:
			return SE;
		    default:
			return null;
		}
	    }

	    /**
	     * Returns the next quadrant in sequence. This is used during
	     * iteration so that the iterator knows how to fetch the next
	     * child. This process converts the quadrant into a numerical
	     * value, increments it, and returns the {@code Quadrant}
	     * representation. This method assumes that the integer
	     * representations of the quadrants are consecutive, starting at
	     * value 0.
	     * 
	     * @return the next quadrant to examine, or {@code null} if there
	     * are no more
	     */
	    static Quadrant next(Quadrant quadrant) {
		int intQuadrant = toInt(quadrant);
		return toQuadrant(++intQuadrant);
	    }

	    /**
	     * Returns the quadrant of the bounds that the point lies within.
	     * 
	     * @param envelope the area encompassing the quadrants
	     * @param point the point to check
	     * @return the quadrant the point lies within, or {@code null} if
	     * the point is out of bounds
	     */
	    static Quadrant determineQuadrant(Envelope envelope, Point point) {
		double[] coords = Envelope.organizeCoordinates(envelope);

		// check if it is out of bounds
		if (point.x < coords[Coordinate.iX_MIN] ||
			point.x > coords[Coordinate.iX_MAX] ||
			point.y < coords[Coordinate.iY_MIN] ||
			point.y > coords[Coordinate.iY_MAX]) {
		    return null;
		}

		// otherwise, try to locate its quadrant
		Point middle = Envelope.calculateMiddle(envelope);
		if (point.x < middle.x) {
		    if (point.y < middle.y) {
			return SW;
		    } else {
			return NW;
		    }
		} else {
		    if (point.y < middle.y) {
			return SE;
		    } else {
			return NE;
		    }
		}
	    }

	    /**
	     * Returns an integer representation of the quadrant of interest.
	     * 
	     * @param envelope the area encompassing the quadrants
	     * @param point the point to search for
	     * @return the quadrant, or -1 if the point is out of bounds
	     */
	    static int determineQuadrantAsInt(Envelope envelope, Point point) {
		return toInt(determineQuadrant(envelope, point));
	    }
	} // end Quadrant

	/** the default starting value for the data integrity variable */
	private static final int DEFAULT_INTEGRITY_START_VALUE =
		Integer.MIN_VALUE;

	/** the parent of this node */
	private final Node<T> parent;

	/** the depth of the node, which will not change */
	private final int depth;

	/** the maximum capacity of a leaf */
	private final int bucketSize;
	
	/**
	 * the integrity value used by the iterators to check for a
	 * {@code ConcurrentModificationException}
	 */
	private int dataIntegrityValue;

	/**
	 * the maximum depth of the tree, as specified by the
	 * {@code ConcurrentQuadTree} class
	 */
	private final int maxDepth;

	/** the branching factor for each node */
	final int numChildren = 4;

	/**
	 * the area (determined by two corner points) representing the node's
	 * bounds
	 */
	private final Envelope envelope;

	/** the quadrant this node belongs to */
	private Quadrant myQuadrant;

	/** the number of stored elements */
	private int size;

	/** the entry of the node if it is a leaf node */
	private List<Entry<T>> values;

	/** references to the children */
	private Node<T>[] children;

	/**
	 * Constructor to be used when instantiating the root. If children
	 * need to be instantiated, call the four-argument constructor.
	 * 
	 * @param envelope the region corresponding to this node's envelope
	 * @param maxDepth the maximum depth of the entire quadtree, which
	 * will subsequently be handed to any children constructed
	 * @param bucketSize the maximum capacity of a leaf node
	 */
	Node(Envelope envelope, int maxDepth, int bucketSize) {
	    this.envelope = envelope;
	    this.parent = null;
	    this.maxDepth = maxDepth;
	    this.bucketSize = bucketSize;
	    dataIntegrityValue = DEFAULT_INTEGRITY_START_VALUE;
	    myQuadrant = null;
	    children = null;
	    values = null;
	    size = 0;
	    depth = 0;
	}

	/**
	 * Creates a {@code Node} which is to be a child. This constructor
	 * increments the depth, whereas the three-argument constructor does
	 * not. The {@code quadrant} argument must not be null.
	 * 
	 * @param parent the parent of the {@code Node}
	 * @param quadrant the {@code Quadrant} which this {@code Node}
	 * represents
	 * @param bucketSize the maximum capacity of a leaf node
	 */
	Node(Node<T> parent, Quadrant quadrant, int bucketSize) {
	    assert (quadrant != null) : "The quadrant cannot be null";
	    envelope = Envelope.createBounds(parent.getEnvelope(), quadrant);

	    this.parent = parent;
	    this.depth = parent.depth + 1;
	    this.maxDepth = parent.maxDepth;
	    this.bucketSize = bucketSize;
	    dataIntegrityValue = DEFAULT_INTEGRITY_START_VALUE;
	    myQuadrant = quadrant;
	    children = null;
	    values = null;
	    size = 0;
	}

	/**
	 * Returns the leaf node associated with the given point by performing
	 * a walk of the tree starting at the given root.
	 * 
	 * @param node the {@code Node} to start searching from
	 * @param point the point belonging to the {@code Node}
	 * @return the {@code Node} corresponding to the given {@code Point},
	 * or null if the point is out of bounds
	 */
	static <T> Node<T> getLeafNode(Node<T> node, Point point) {
	    if (!node.isLeaf()) {
		Quadrant q =
			Node.Quadrant.determineQuadrant(node.getEnvelope(),
				point);
		return getLeafNode(node.getChild(q), point);
	    }
	    return node;
	}
	
	
	/**
	 * Checks whether there exists an entry in the list with the same
	 * coordinate as the given point.
	 * @param <T> the type of the elements stored in the list
	 * @param point the coordinate to check
	 * @param entries the list of elements
	 * @return {@code true} if there is an {@code Entry} with the same
	 * coordinate, and {@code false} otherwise
	 */
	private static <T> boolean contains(Point point, List<Entry<T>> entries) {
	    if (entries == null || entries.size() == 0) {
		return false;
	    }
	    Entry<T> entry;
	    Iterator<Entry<T>> iter = entries.iterator();
	    
	    // Iterate through all the entries checking for the instance
	    while (iter.hasNext()) {
		entry = iter.next();
		if (entry.coordinate.equals(point)) {
		    return true;
		}
	    }
	    return false;
	}
	
	
	public String toString() {
	    if (isLeaf()) {
		if (values != null) {
		    return values.toString();
		} 
		return null;
	    }
	    StringBuilder sb = new StringBuilder("[");
	    for (int i=0 ; i<numChildren ; i++) {
		if (i > 0 ) {
		    sb.append(", ");
		}
		sb.append(children[i].toString());
	    }
	    sb.append("]");
	    return sb.toString();
	}

	/**
	 * Retrieves the data integrity value for this node.
	 * 
	 * @return the data integrity value
	 */
	int getDataIntegrityValue() {
	    return dataIntegrityValue;
	}

	/**
	 * Returns the quadrant that this node represents
	 * 
	 * @return the quadrant that this node represents
	 */
	Quadrant getQuadrant() {
	    return myQuadrant;
	}

	/**
	 * Returns the child corresponding to the given quadrant
	 * 
	 * @param quadrant the quadrant of the parent to retrieve
	 * @return the child corresponding to the given quadrant
	 * @throws IndexOutOfBoundsException if the index is out of
	 * bounds
	 */
	Node<T> getChild(Quadrant quadrant) {
	    assert (!isLeaf()) : "The node is a leaf node";
	    int index = Quadrant.toInt(quadrant);
	    return children[index];
	}
	
	
	/**
	 * Returns the child corresponding to the given index
	 * 
	 * @param quadrant the quadrant of the parent to retrieve
	 * @return the child corresponding to the given quadrant
	 * @throws IndexOutOfBoundsException if the index is out of
	 * bounds
	 */
	Node<T> getChild(int index) {
	    assert (!isLeaf()) : "The node is a leaf node";
	    return children[index];
	}

	/**
	 * Returns the corner points of the region corresponding to this node
	 * 
	 * @return the corner points of the region corresponding to this node
	 */
	Envelope getEnvelope() {
	    return envelope;
	}
	
	
	/**
	 * Attempts to retrieve the entry at the given coordinate.
	 * @param point the {@code point} to examine for an entry
	 * @return the value at the given coordinate, or {@code null} if none
	 * exist
	 */
	Entry<T> get(Point point) {
	    if (values == null) {
		return null;
	    }
	    Iterator<Entry<T>> iter = values.iterator();
	    Entry<T> entry = null;
	    while (iter.hasNext()) {
		entry = iter.next();
		
		// Break if we found a matching coordinate
		if (entry.coordinate.equals(point)) {
		    return entry;
		}
	    }
	    return null;
	}

	/**
	 * Throws an {@code IllegalArgumentException} if the coordinates are
	 * out of bounds of the sample space.
	 * 
	 * @param x the x-coordinate
	 * @param y the y-coordinate
	 * @throws IllegalArgumentException if the coordinates are not within
	 * the bounds of the sample space
	 */
	void isPointWithinBounds(double x, double y) {
	    Point[] bounds = envelope.bounds;

	    if (x < Math.min(bounds[0].x, bounds[1].x) ||
		    x > Math.max(bounds[0].x, bounds[1].x) ||
		    y < Math.min(bounds[0].y, bounds[1].y) ||
		    y > Math.max(bounds[0].y, bounds[1].y)) {
		throw new IllegalArgumentException("The coordinates (" + x +
			", " + y + ") are out of bounds");
	    }
	}

	/**
	 * Returns the number of coordinates contained within this node and
	 * all its children. This method runs in constant time, unless this
	 * {@code Node} is the root. If it is the root, then it runs in linear
	 * time proportional to {@code numChildren}.
	 * 
	 * @return the number of {@code CoordinatePair}s stored at this level
	 * (if it is a leaf node), or of its children that are leaf nodes
	 */
	int size() {
	    // If this is an intermediate node, then just return the size
	    if (parent != null) {
		return size;
	    }
	    // If this is a root node with no children, obtain the size
	    // of the element list
	    if (children == null) {
		return (values == null? 0 : values.size());
	    }

	    // Otherwise, the root node has children. Therefore, aggregate 
	    // the values of the children to improve concurrency
	    int totalSize = 0;
	    for (int i = 0; i < numChildren; i++) {
		Node<T> node = children[i];
		totalSize += node.size();
	    }
	    return totalSize;
	}

	/**
	 * Walk up the tree, decrementing the parent's count value
	 */
	void decrementSize() {
	    // End condition: we will stop percolating when we
	    // reach the root node
	    if (parent == null) {
		if (size() == 0) {
		    convertToLeafNode();
		}
		return;
	    }
	    
	    // Remove each child from the data store
	    if (--size == 0) {
		convertToLeafNode();
	    }
	    parent.decrementSize();
	}

	/**
	 * Sets the children and quadrant to {@code null}.
	 */
	private void convertToLeafNode() {
	    children = null;
	    myQuadrant = null;
	}
	
	/**
	 * Walk up the tree, incrementing the parent's count value
	 */
	void incrementSize() {
	    // End condition: we will stop percolating when we
	    // reach the root node
	    if (parent == null) {
		return;
	    }
	    size++;
	    parent.incrementSize();
	}

	/**
	 * Returns the depth of this node in the tree.
	 * 
	 * @return the depth of this node in the tree
	 */
	int getDepth() {
	    return depth;
	}

	/**
	 * Returns this node's parent.
	 * 
	 * @return this node's parent, or {@code null} if it is the root
	 */
	Node<T> getParent() {
	    return parent;
	}

	/**
	 * Returns this node's {@code Entry}, which consists of a
	 * {@code Point}-element pair
	 * 
	 * @return this node's {@code Entry}
	 */
	List<Entry<T>> getValues() {
	    return values;
	}


	/**
	 * Attempts to set the value to the given {@code CoordinatePair}.
	 * This will throw an {@code IllegalStateException} if called when
	 * this node is not a leaf node. The value should not be set to null
	 * unless this node is becoming a parent.
	 * 
	 * @param value the value to set this node to
	 * @return the old element, or {@code null} if one didn't exist
	 * @throws IllegalStateException if the node is not a leaf node
	 */
	T setValue(Point coord, T value) throws IllegalStateException {
	    assert (value != null) : "Value cannot be null";
	    
	    if (!isLeaf()) {
		throw new IllegalStateException("The node is not a leaf node");
	    }
	    Entry<T> entry = get(coord);
	    if (entry != null) {
		T old = entry.getValue();
		entry.setValue(value);
		return old;
	    }
	    return null;
	}

	
	
	/**
	 * Determines if this node is a leaf node.
	 * 
	 * @return {@code true} if it is a leaf node, and {@code false}
	 * otherwise
	 */
	boolean isLeaf() {
	    return (children == null);
	}

	/**
	 * Adds the entry to the node. This call is equivalent to
	 * {@code add(entry.coordinate, entry.value);}.
	 * 
	 * @param entry the entry to add to the quadtree
	 * @param propagate {@code true} if the size should be propagated
	 * to the root, and {@code false} otherwise
	 * @return {@code true} if the element was successfully added, and
	 * {@code false} otherwise
	 */
	private boolean add(Entry<T> entry, boolean propagate) {
	    return add(entry.coordinate, entry.value, propagate);
	}

	/**
	 * Adds the element to the node. If the node is already populated with
	 * a value, then a split operation occurs which generates children and
	 * converts this node from a leaf into an intermediate node.
	 * 
	 * @param point the coordinate to add the element
	 * @param element the element to add
	 * @param propagate {@code true} if the size should be propagated
	 * to the root, and {@code false} otherwise
	 * @return {@code true} if the element was successfully added, and
	 * {@code false} otherwise
	 */
	boolean add(Point point, T element, boolean propagate) {
	    Entry<T> newEntry = new Entry<T>(point, element);

	    /*
	     * If there aren't any values yet, a new list is instantiated. If
	     * there already is a list, only add if it doesn't already exist.
	     * Otherwise if we are at capacity, perform a split.
	     */
	    if (values == null) {
		assert (values.size() == 0) : "Size was not zero for Node.add()";
		values = new ArrayList<Entry<T>>(bucketSize);
		append(newEntry, propagate);
		
	    } else if (size() < bucketSize) {
		// TODO: do we want to allow duplicate entries?
		if (contains(point, values)) {
		    return false;
		}
		append(newEntry, propagate);
		
	    } else {
		// Check if we reached the maximum depth of the tree.
		// If so, we cannot split since it will increase tree depth.
		if (depth == maxDepth) {
		    return false;
		}
		return splitThenAdd(point, element);
	    }
	    return true;
	}

	
	/**
	 * extract the old value and clear; it no longer should have a value
	 * since this node will soon have children
	 * 
	 * @param leaf the node to split
	 * @param point the coordinate to add the element
	 * @param element the element to add
	 * @return {@code true} if the element was successfully added, and
	 * {@code false} otherwise
	 */
	private boolean splitThenAdd(Point point, T element) {
	    size = values.size();
	    
	    int quadrant;
	    List<Entry<T>> existingValues = values;
	    initializeNewChildren();

	    // Add back the old elements to the appropriate new leaves.
	    // Since we have four new quadrants, we have to add each
	    // one individually to allocate it in the correct quadrant.
	    for (int i = 0; i < existingValues.size(); i++) {
		Entry<T> entry = existingValues.get(i);

		quadrant =
			Quadrant.determineQuadrantAsInt(envelope,
				entry.coordinate);
		children[quadrant].add(entry, false);
	    }

	    // add in the new value, making sure to propagate
	    quadrant = Quadrant.determineQuadrantAsInt(envelope, point);
	    return children[quadrant].add(point, element, true);
	}
	
	
	/**
	 * Appends the entry to the leaf node's list
	 * @param entry the new entry to append
	 * @param propagate {@code true} if the size should be propagated
	 * to the root, and {@code false} otherwise
	 */
	private void append(Entry<T> entry, boolean propagate) {
	    assert (isLeaf()) : "The node is not a leaf";

	    values.add(entry);
	    size++;
	    dataIntegrityValue++;
	    
	    if (propagate && parent != null) {
		parent.incrementSize();
	    }
	}
	
	/**
	 * Initializes the children so that new elements can be added. This
	 * process sets the value of the current node to {@code null} in
	 * anticipation of new children to be instantiated.
	 * 
	 * @return an initialized {@code Node} array fit to store children
	 */
	@SuppressWarnings("unchecked")
	void initializeNewChildren() {
	    values = null;
	    children = new Node[numChildren];

	    // Initialize each direction separately
	    children[Quadrant.iNE] = new Node<T>(this, Quadrant.NE, bucketSize);
	    children[Quadrant.iNW] = new Node<T>(this, Quadrant.NW, bucketSize);
	    children[Quadrant.iSE] = new Node<T>(this, Quadrant.SE, bucketSize);
	    children[Quadrant.iSW] = new Node<T>(this, Quadrant.SW, bucketSize);
	}

	
	/**
	 * Removes the entry located at the supplied index. This method is intended
	 * to be called from the iterator so that a traversal through the list is 
	 * not performed.
	 * @param index the index of the entry in the list
	 * @return the removed element which cannot be {@code null}
	 * @throws IndexOutOfBoundsException if the supplied index is out of bounds
	 */
	T iteratorRemove(int index) {
	    Entry<T> old = values.remove(index);
	    dataIntegrityValue++;
	    doRemoveWork();
	    return old.getValue();
	}
	
	
	/**
	 * Decrements the size and propagates the changes up
	 */
	private void doRemoveWork() {
	    // set the values to null if it was the last entry
	    if (--size == 0) {
		values = null;
	    }
	    // walk up the tree, collapsing empty subtrees along the way
	    if (parent != null) {
		parent.decrementSize();
	    }
	}
	
	
	/**
	 * Removes the element from the tree if it exists.
	 * 
	 * @param coordinate the coordinate of the element to remove
	 * @return the element that was removed, or {@code null} if none was
	 * removed
	 */
	T remove(Point coordinate) {

	    // If there was no value stored, or if the node has children,
	    // return null since we cannot remove anything from this node
	    if (values == null || children != null) {
		return null;
	    }
	    
	    Iterator<Entry<T>> iter = values.iterator();
	    Entry<T> entry;
	    while (iter.hasNext()) {
		entry = iter.next();
		
		// If one of the elements has the same coordinate,
		// then we will remove it
		if (entry.coordinate.equals(coordinate)) {
		    T removed = entry.value;
		    iter.remove();
		    
		    dataIntegrityValue++;
		    doRemoveWork();
		    return removed;
		}
	    }
	    return null;
	}
    }
}