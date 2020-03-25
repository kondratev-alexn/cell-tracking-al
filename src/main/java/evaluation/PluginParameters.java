package evaluation;

import java.nio.file.Path;

/**
 * Plugin parameters wrapper
 * @author Александр
 *
 */
public class PluginParameters {
	
	public PluginParameters(float softmaxThreshold, int minArea, int maxArea, float minCirc, float maxCirc,
			int minTrackLength, boolean trackMitosis, boolean filterComponents, boolean useWatershedPostProcessing, 
			boolean addRois, boolean noImageJProcessing, boolean saveSegmentation, Path noWshedResults) {
		this.softmaxThreshold = softmaxThreshold;
		this.minArea = minArea;
		this.maxArea = maxArea;
		this.minCirc = minCirc;
		this.maxCirc = maxCirc;
		this.minTrackLength = minTrackLength;
		this.trackMitosis = trackMitosis;
		this.filterComponents = filterComponents;
		this.useWatershedPostProcessing = useWatershedPostProcessing;
		this.addRois = addRois;
		this.noImageJProcessing = noImageJProcessing;
		this.saveSegmentation = saveSegmentation;
		this.destinationFolder = noWshedResults;
	}
	
	public float softmaxThreshold;
	public int minArea;
	public int maxArea;
	public float minCirc;
	public float maxCirc;
	public int minTrackLength;
	public boolean trackMitosis;
	public boolean filterComponents;
	public boolean useWatershedPostProcessing;
	public boolean addRois;
	public boolean noImageJProcessing;
	public boolean saveSegmentation;
	
	public Path destinationFolder;
}
