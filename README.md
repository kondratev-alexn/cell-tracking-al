This is a project implementing cell segmentation. You can find detailed information about the algorithm, its parameters and usage below.

## Usage 
1. As with all plugins, place .jar file into the plugins ImageJ folder

2. Start ImageJ. Start the plugin by Plugins->Cell Tracking (or it might be in Plugins->cellTracking->Cell Tracker, bit unlikely)

3. Put Image sequence of the second channel (488 excitation). You can try the plugin on the other channel, but you will have to adjust parameters for good results.

4. At the moment, parameters (incuding boolean ones, like use median filter or not) are adjusted so that segmentation for the first 20 images in
170704 - c0010901 is almost perfect. There are some components, where the regions is a bit larger\smaller, than the nucleus. And in the last slice
№20 there is one component that belongs to tha background rather than the cell (You cannot filter it by circularity, because its circularity is higher than that of some cells). So this requires improvement.

5. After you have chosen the parameters, you can click preview to see watershedding result for the selected slice, and if "Filter components" is checked, the components will be filtered according to their area and circularity.

6. To get the rois, please, (!!!) uncheck the preview button, check "Filter Components", click "OK", choose "Yes" when asked if process the entire stack and wait. You can interact with the roi manager during this, for example, check "Show All" and watch the rois appear on the slices.
I didn't test other interations with the roi manager during the processing time, so it's better not to do it.

7. The rois should appear in roi manager after each slice is processed.

8. After processing, you can access acquired roi in the roi manager. All rois are dedicated to their slices and the roi label format is following:
"<slice number>-<intensity>", where <intensity> is intensity used for internal representation of the components (basically, component ID).

9. To show rois for only the selected slice, go to "More" >> "Options..." and check [Associate "Show All" ROIs with Slices"] box

Note 1: The tracking is yet to be implemented, along with many improvements. Now it's more like a showcase of what can be done.

Note 2: There are still a lot of bugs/inconviniences in usage, so I'm not sure what might happen if one does not follow these instructions step-by-step.
In this case, one can expect errors, empty results or corruption of the original image (not the file, just the one loaded into ImageJ).
________________________________________________________________________________________________


## Algorithm

#### Preprocessing:

	1. Median filtration of the original image (optional)
	2. Background subtraction
	3. Bandpass Filtering (optional)
	4. Morphological Closing
	
#### Segmentation:

	5. Find markers in bandpassed (or original) image
	6. Merge markers, using components, detected in the previous slice. Namely, it first creates dilated (with radius) masks of each component. 
	   Then, it finds markers, that belong to the same mask (in the previous slice), but correspond to small components in current slice (that way, markers found on the background, won't be used) and merges them into a single mark (actually, geometrical centre of found markers).
	   It's not really perfect, because markers of the cell in the current slice can appear in mask of another cell from previous slice, thus merging two cells.
	   To avoid this, small enough dilation radius should be used. Though it also will not help if some cell moves too fast between the slices.
	   P.S.: this can also be used for simple tracking, since we know "previous component" - "current mark" connection,  but I haven't utilized it yet.
	7. Find image gradient for watershedding.
	   The derivatives for gradient are found by convolving the image with derivatives of the Gaussian function.
	8. Marker controlled watershedding on the gradient of bandpassed (or original) image.
	9. Filter components by size and circularity and add filtered components to roi manager.
	
I use the single roi manager for the whole stack, since rois are assigned to the slices. 

________________________________________________________________________________________________

## Pseudo-code of the algorithm
```
if (useMedian)
	medianFiltration(image, medRadius);
subtractBackground(image, rbRadius);
if (isBandpass)
	bandpassFilter(image, sigma1, sigma2);
morpholibj.Closing(image, DISK, clRadius);
marker = findMaxima(image, heightTolerance, SINGLE_POINTS, excludeOnEdges = true);
mergeMarkers(marker, previousComponents, dilationRadius);
gradient = GaussianGradient(image, sigma3);
components = markerControlledWatershedTransform(gradient, marker, Mask = null, Connectivity = 4);
if (filter) {
	filterComponents(components, areaRange, circularityRange);
	addRoisToManager(components, roiManager, currentSlice);
}
```
   
________________________________________________________________________________________________

## Parameters 
Median filter radius (medRadius)  
	-radius of median filter
	
Rolling ball radius (rbRadius)  
	-radius, used in rolling ball background subtraction algorithm
	
Closing radius	(clRadius)  
	-radius of morphological closing before
	
sigma1 (bandpass)  
sigma2 (bandpass)  
	-sigmas for bandpass filtering. ( bandpassedImage = GaussianFilter(image, sigma1) - GaussianFilter(image, sigma2) )
	
sigma3 (gradient)  
	-sigma used to smooth the image before computing the gradient
	
Height tolerance (max find)  
	-parameter used in maximaFind algorithm
	
Min area  
Max area  
	-Used for filtering the components by area. Only components with area in [minArea; maxArea] are left
	
Min circularity  
Max circularity  
	-Used for filtering the components by circularity. Only components with circularity in [minCircularity; maxCircularity] are left.  
Note: circularity = 4*Pi* (Area) / (Perimeter^2)
	
Dilation Radius (postprocessing)  
	-Used to make masks of components, detected in the previous slices.