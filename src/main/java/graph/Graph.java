package graph;

import java.util.ArrayList;

/* let it be connected undirected graph (should be sufficient for trees) */
public class Graph {
	private ArrayList<Node> nodes; // vertices of the graph
	private ArrayList<Arc> arcs; // edges of the graph

	// TODO when adding arcs add the information about it in nodes or in adj matrix
	// to use the graph easily
	ArrayList<ArrayList<Integer>> adjLists;

	public Graph() {
		nodes = new ArrayList<Node>(5);
		arcs = new ArrayList<Arc>(5);
		adjLists = new ArrayList<ArrayList<Integer>>();
	}

	/* plain add node into set of nodes */
	public void addNode(Node v) {

		nodes.add(v);
		adjLists.add(new ArrayList<Integer>());
	}

	public void addNodeByTimeAndIndex(int time, int index) {
		Node v = new Node(time, index);
		addNode(v);
	}

	public void addArc(Arc arc) {
		arcs.add(arc);
		int i = nodes.indexOf(arc.getFromNode());
		int j = nodes.indexOf(arc.getToNode());
		adjLists.get(i).add(j);
	}

	/* adds the arc only if both nodes are in the graph */
	public void addArcFromTo(Node from, Node to) {
		if (isNodeInGraph(from) && isNodeInGraph(to)) {
			Arc arc = new Arc(from, to);
			addArc(arc);
		}
	}

	public void addNodeToNode(Node nodeInGraph, Node newNode) {
		nodes.add(newNode);
		addArcFromTo(nodeInGraph, newNode);
	}

	/*
	 * adds the arc. If the nodes are not in the graph, adds them first, else find
	 * existing nodes and create arc
	 */
	public void addArcFromToAddable(Node from, Node to) {
		if (!isNodeInGraph(from))
			addNode(from);
		else
			from = findNode(from);
		if (!isNodeInGraph(to))
			addNode(to);
		else
			to = findNode(to);
		Arc arc = new Arc(from, to);
		addArc(arc);
	}

	private boolean isNodeInGraph(Node v) {
		if (nodes.contains(v))
			return true;
		return false;
	}

	public boolean checkNoEqualNodes() {
		for (int i = 0; i < nodes.size(); i++) {
			for (int j = 0; j < nodes.size(); j++) {
				Node vi = nodes.get(i);
				Node vj = nodes.get(j);
				if (i != j && vi.equals(vj)) {
					System.out.println("found equals node, indexes are " + i + ", " + j);
					return false;
				}
			}
		}
		return true;
	}

	// If the node exists in the graph, return the existing node, otherwise return
	// the parameter
	private Node findNode(Node v) {
		for (int i = 0; i < nodes.size(); i++) {
			if (v.equals(nodes.get(i)))
				return nodes.get(i);
		}
		return v;
	}

	/* return list of arcs which nodes' time is <= t */
	public ArrayList<Arc> getArcsBeforeTimeSlice(int t) {
		ArrayList<Arc> result = new ArrayList<Arc>(arcs.size() / 10);
		for (int i = 0; i < arcs.size(); i++) {
			Arc a = arcs.get(i);
			if (a.getToNode().get_t() <= t && a.getFromNode().get_t() <= t)
				result.add(a);
		}
		return result;
	}

	public ArrayList<Arc> getArcs() {
		return arcs;
	}

	@Override
	public String toString() {
		return "Graph [nodes=" + nodes + " \n arcs=" + arcs + " \n adjLists=" + adjLists + "]";
	}
}
