This is a project implementing cell nuclei tracking and calculating track statistics.

## Usage
Currently confirmed to work with Fiji.
1. Place .jar file into the plugins Fiji folder

2. Start ImageJ. Start the plugin by Plugins->Nuclei Tracking->Tracking for cell tracking, ->Properties Measure for calculating statistics.

3. The algorithm uses the second channel image sequence (488 excitation) as input. 

4. After you have chosen the parameters, you can click preview to see watershedding result for the selected slice, and if "Filter components" is checked, the components will be filtered according to their area and circularity.

