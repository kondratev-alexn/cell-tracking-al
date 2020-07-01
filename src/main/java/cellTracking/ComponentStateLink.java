package cellTracking;

/**
 * class representing connection between two components during tracking in terms of their State property
 * @author Александр
 *
 */
public class ComponentStateLink {

	State from;
	State to;
	ComponentStateLink(State stateFrom, State stateTo) {
		from = stateFrom;
		to = stateTo;
	}
	
	public boolean checkStates(ImageComponentsAnalysis comp1, int i1, ImageComponentsAnalysis comp2, int i2) {
		return comp1.getComponentState(i1) == from && comp2.getComponentState(i2) == to;
	}
}
