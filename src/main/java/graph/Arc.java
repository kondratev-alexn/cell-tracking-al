package graph;

public class Arc {
	static private int next_id;
	
	private int id;

	private Node from;
	private Node to;
	
	private double score;

	private boolean isDirected = false;

	Arc(Node from, Node to) {
		id = next_id++;
		this.from = from;
		this.to = to;
	}
	
	public void setScore(double score) {
		this.score = score;
	}
}
