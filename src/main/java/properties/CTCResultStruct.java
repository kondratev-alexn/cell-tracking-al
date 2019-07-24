package properties;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import evaluation.PluginRunning;
import ij.ImagePlus;

/**
 * Class wrappin outputs of tracking results
 * @author Александр
 *
 */
public class CTCResultStruct {
	public ImagePlus impTrackingResults;
	public String ctcTracksTxtPath;
	public ImagePlus impOriginal;
	public String noExtensionName;
	
	public CTCResultStruct() {}
	
	public void fillByResultsFolder(String folder) throws IOException {
		ArrayList<String> folders = new ArrayList<String>(1);
		folders.add(folder);
		List<Path> files = PluginRunning.getAllFilePathsFromFolders(folders);
		for (Path f: files) {
			String fileName = f.getFileName().toString(); 
			if (fileName.contains("_tracking_results.tif")) {
				impTrackingResults = new ImagePlus(f.toString());
			}
			else 
				if (fileName.contains(".tif") && !fileName.contains("_mitosis_colored.tif")) {
					impOriginal = new ImagePlus(f.toString());
					noExtensionName = fileName.split(".tif")[0];
				}
			if (fileName.contains("_tracking_results.txt")) {
				ctcTracksTxtPath = f.toString();
			}
		}
	}
	
	@Override
	public String toString() {
		return "CTCResultStruct [impTrackingResults=" + impTrackingResults + ", ctcTracksTxtPath=" + ctcTracksTxtPath
				+ ", impOriginal=" + impOriginal + "]";
	}

}
