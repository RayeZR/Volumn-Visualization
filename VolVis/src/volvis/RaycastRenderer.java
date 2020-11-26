package volvis;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;
import java.awt.image.BufferedImage;

import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;
import volume.VoxelGradient;

import java.lang.System;

/**
 * Raycast Renderer.
 *
 * @author Michel Westenberg
 * @author Anna Vilanova
 * @author Nicola Pezzotti
 * @author Humberto Garcia
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {
    
    private double k_a = 0.1;
    private double k_d = 0.7;
    private double k_s = 0.2;
    private int hl_alpha = 100;
    private double eps = 1e-5;

    /**
     * Volume that is loaded and visualized.
     */
    private Volume volume = null;

    /**
     * Rendered image.
     */
    private BufferedImage image;

    /**
     * Gradient information of the loaded volume.
     */
    private GradientVolume gradients = null;

    /**
     * Reference to the GUI panel.
     */
    RaycastRendererPanel panelFront;

    /**
     * Transfer Function.
     */
    TransferFunction tFuncFront;

    /**
     * Reference to the GUI transfer function editor.
     */
    TransferFunctionEditor tfEditor;

    /**
     * Transfer Function 2D.
     */
    TransferFunction2D tFunc2DFront;

    /**
     * Reference to the GUI 2D transfer function editor.
     */
    TransferFunction2DEditor tfEditor2DFront;

    /**
     * Mode of our raycast. See {@link RaycastMode}
     */
    private RaycastMode modeFront;

    /**
     * Whether we are in cutting plane mode or not.
     */
    private boolean cuttingPlaneMode = false;

    /**
     * Whether we are in shading mode or not.
     */
    private boolean shadingMode = false;

    /**
     * Iso value to use in Isosurface rendering.
     */
    private float isoValueFront = 95f;

    /**
     * Color used for the isosurface rendering.
     */
    private TFColor isoColorFront;

    // Below cutting plane specific attributes
    /**
     * Cutting plane normal vector.
     */
    private final double[] planeNorm = new double[]{0d, 0d, 1d};

    /**
     * Cutting plane point.
     */
    private final double[] planePoint = new double[]{0d, 0d, 0d};

    /**
     * Back mode of our raycast for cutting plane.
     */
    private RaycastMode modeBack;

    /**
     * Iso value to use in Isosurface rendering for cutting plane.
     */
    private float isoValueBack = 95f;

    /**
     * Color used for the isosurface rendering for cutting plane.
     */
    private TFColor isoColorBack;

    /**
     * Transfer Function for cutting plane.
     */
    TransferFunction tFuncBack;

    /**
     * Reference to the GUI transfer function editor for cutting plane.
     */
    TransferFunctionEditor tfEditorBack;

    /**
     * Transfer Function 2D for cutting plane.
     */
    TransferFunction2D tFunc2DBack;

    /**
     * Reference to the GUI 2D transfer function editor for cutting plane.
     */
    TransferFunction2DEditor tfEditor2DBack;

    /**
     * Constant Zero gradient.
     */
    private final static VoxelGradient ZERO_GRADIENT = new VoxelGradient();

    /**
     * Gets the corresponding voxel using Nearest Neighbors.
     *
     * @param coord Pixel coordinate in 3D space of the voxel we want to get.
     * @return The voxel value.
     */
    private short getVoxel(double[] coord) {
        // Get coordinates
        double dx = coord[0], dy = coord[1], dz = coord[2];

        // Verify they are inside the volume
        if (dx < 0 || dx >= volume.getDimX() || dy < 0 || dy >= volume.getDimY()
                || dz < 0 || dz >= volume.getDimZ()) {

            // If not, jus return 0
            return 0;
        }

        // Get the closest x, y, z to dx, dy, dz that are integers
        // This is important as our data is discrete (not continuous)
        int x = (int) Math.floor(dx);
        int y = (int) Math.floor(dy);
        int z = (int) Math.floor(dz);

        // Finally, get the voxel from the Volume for the corresponding coordinates
        return volume.getVoxel(x, y, z);
    }

    /**
     * Gets the corresponding voxel using Tri-linear Interpolation.
     *
     * @param coord Pixel coordinate in 3D space of the voxel we want to get.
     * @return The voxel value.
     */
    private short getVoxelTrilinear(double[] coord) {
        // TODO 1: Implement Tri-Linear interpolation and use it in your code
        // instead of getVoxel().
        double dx = coord[0], dy = coord[1], dz = coord[2];
        // Verify they are inside the volume
        if (dx < 0 || dx >= volume.getDimX() || dy < 0 || dy >= volume.getDimY()
                || dz < 0 || dz >= volume.getDimZ()) {
            // If not, jus return 0
            return 0;
        }
        
        int xl = (int) Math.floor(dx);
        int yl = (int) Math.floor(dy);
        int zl = (int) Math.floor(dz);
        
        double alpha = dx - xl;
        double beta = dy - yl;
        double gamma = dz - zl;
//        System.out.printf("%f, %f, %f\n", alpha, beta, gamma);

        int xu = volume.getDimX() - 1 > xl ? (xl + 1) : xl;
        int yu = volume.getDimY() - 1 > yl ? (yl + 1) : yl;
        int zu = volume.getDimZ() - 1 > zl ? (zl + 1) : zl;

        
        double sx0 = volume.getVoxel(xl, yl, zl);
        double sx1 = volume.getVoxel(xu, yl, zl);
        double sx2 = volume.getVoxel(xl, yu, zl);
        double sx3 = volume.getVoxel(xu, yu, zl);
        double sx4 = volume.getVoxel(xl, yl, zu);
        double sx5 = volume.getVoxel(xu, yl, zu);
        double sx6 = volume.getVoxel(xl, yu, zu);
        double sx7 = volume.getVoxel(xu, yu, zu);
        
//        int xs = (int) Math.floor(2.0);
//        int xs1 = (int) Math.ceil(2.0);
//        System.out.printf("%d %d\n", xs, xs1);
        
        double sx = (1 - alpha) * (1-beta) * (1-gamma) * sx0 + alpha * (1 - beta) *(1 - gamma) * sx1 +
                    (1 - alpha) * beta * (1 - gamma) * sx2 + alpha * beta * (1 - gamma)* sx3 +
                    (1 - alpha) * (1 - beta) * gamma * sx4 + alpha * (1 - beta) * gamma * sx5 +
                    (1 - alpha) * beta * gamma * sx6 + alpha * beta * gamma * sx7;
        
        return (short) sx;
    }

    /**
     * Gets the corresponding VoxelGradient using Nearest Neighbors.
     *
     * @param coord Pixel coordinate in 3D space of the voxel we want to get.
     * @return The voxel gradient.
     */
    private VoxelGradient getGradient(double[] coord) {
        // Get the coordinates
        double dx = coord[0], dy = coord[1], dz = coord[2];

        // Verify they are inside the volume gradient
        if (dx < 0 || dx > (gradients.getDimX() - 2) || dy < 0 || dy > (gradients.getDimY() - 2)
                || dz < 0 || dz > (gradients.getDimZ() - 2)) {

            // If not, just return a zero gradient
            return ZERO_GRADIENT;
        }

        // Get the closest x, y, z to dx, dy, dz that are integers
        // This is important as our data is discrete (not continuous)
        int x = (int) Math.round(dx);
        int y = (int) Math.round(dy);
        int z = (int) Math.round(dz);

        // Finally, get the gradient from GradientVolume for the corresponding coordinates
//        VoxelGradient grad = gradients.getGradient(x, y, z);
//        System.out.printf("%f, %f, %f\n", grad.x, grad.y, grad.z);
//        System.out.printf("%d, %d, %d\n", x, y, z);
//        System.out.printf("%f\n", gradients.getGradient(87, 98, 94).x);
//        float[] vec = gradients.getGradientVec(x, y, z);
//        System.out.printf("%f, %f, %f\n", vec[0], vec[1], vec[2]);
        return gradients.getGradient(x, y, z);
    }

    /**
     * Gets the corresponding VoxelGradient using Tri-linear interpolation.
     *
     * @param coord Pixel coordinate in 3D space of the voxel we want to get.
     * @return The voxel gradient.
     */
    private VoxelGradient getGradientTrilinear(double[] coord) {
        // TODO 6: Implement Tri-linear interpolation for gradients
//        return ZERO_GRADIENT;

        // Get the coordinates
        double dx = coord[0], dy = coord[1], dz = coord[2];

        // Verify they are inside the volume gradient
        if (dx < 0 || dx > (gradients.getDimX() - 2) || dy < 0 || dy > (gradients.getDimY() - 2)
                || dz < 0 || dz > (gradients.getDimZ() - 2)) {

            // If not, just return a zero gradient
            return ZERO_GRADIENT;
        }

        // Get the closest x, y, z to dx, dy, dz that are integers
        // This is important as our data is discrete (not continuous)
        int xl = (int) Math.floor(dx);
        int yl = (int) Math.floor(dy);
        int zl = (int) Math.floor(dz);
        
        float alpha = (float) (dx - xl);
        float beta = (float) (dy - yl);
        float gamma = (float) (dz - zl);
//        System.out.printf("%f, %f, %f\n", alpha, beta, gamma);

        int xu = gradients.getDimX() - 1 > xl ? (xl + 1) : xl;
        int yu = gradients.getDimY() - 1 > yl ? (yl + 1) : yl;
        int zu = gradients.getDimZ() - 1 > zl ? (zl + 1) : zl;

        
        float[] sx0 = gradients.getGradientVec(xl, yl, zl);
        float[] sx1 = gradients.getGradientVec(xu, yl, zl);
        float[] sx2 = gradients.getGradientVec(xl, yu, zl);
        float[] sx3 = gradients.getGradientVec(xu, yu, zl);
        float[] sx4 = gradients.getGradientVec(xl, yl, zu);
        float[] sx5 = gradients.getGradientVec(xu, yl, zu);
        float[] sx6 = gradients.getGradientVec(xl, yu, zu);
        float[] sx7 = gradients.getGradientVec(xu, yu, zu);
        
        float[] grad_vec = new float[3];
        for(int i = 0; i < 3; i++){
            grad_vec[i] = (1 - alpha) * (1-beta) * (1-gamma) * sx0[i] + alpha * (1 - beta) *(1 - gamma) * sx1[i] +
                 (1 - alpha) * beta * (1 - gamma) * sx2[i] + alpha * beta * (1 - gamma)* sx3[i] +
                 (1 - alpha) * (1 - beta) * gamma * sx4[i] + alpha * (1 - beta) * gamma * sx5[i] +
                 (1 - alpha) * beta * gamma * sx6[i] + alpha * beta * gamma * sx7[i];
        }
        
        VoxelGradient sx = new VoxelGradient(grad_vec[0], grad_vec[1], grad_vec[2]);

        // Finally, get the gradient from GradientVolume for the corresponding coordinates
        return sx;
    }

    /**
     * Updates {@link #image} attribute (result of rendering) using the slicing
     * technique.
     *
     * @param viewMatrix OpenGL View matrix {
     * @see
     * <a href="www.songho.ca/opengl/gl_transform.html#modelview">link</a>}.
     */
    private void slicer(double[] viewMatrix) {

        // Clear the image
        resetImage();

        // vector uVec and vVec define a plane through the origin,
        // perpendicular to the view vector viewVec which is going from the view point towards the object
        // uVec contains the up vector of the camera in world coordinates (image vertical)
        // vVec contains the horizontal vector in world coordinates (image horizontal)
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // We get the size of the image/texture we will be puting the result of the 
        // volume rendering operation.
        int imageW = image.getWidth();
        int imageH = image.getHeight();

        int[] imageCenter = new int[2];
        // Center of the image/texture 
        imageCenter[0] = imageW / 2;
        imageCenter[1] = imageH / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        double max = volume.getMaximum();

        TFColor pixelColor = new TFColor();
        // Auxiliar color
        TFColor colorAux;

        for (int j = imageCenter[1] - imageH / 2; j < imageCenter[1] + imageH / 2; j++) {
            for (int i = imageCenter[0] - imageW / 2; i < imageCenter[0] + imageW / 2; i++) {
                // computes the pixelCoord which contains the 3D coordinates of the pixels (i,j)
                computePixelCoordinatesFloat(pixelCoord, volumeCenter, uVec, vVec, i, j);

//                int val = getVoxel(pixelCoord);
                //NOTE: you have to implement this function to get the tri-linear interpolation
                int val = getVoxelTrilinear(pixelCoord);

                // Map the intensity to a grey value by linear scaling
                pixelColor.r = val / max;
                pixelColor.g = pixelColor.r;
                pixelColor.b = pixelColor.r;
                pixelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                // Alternatively, apply the transfer function to obtain a color
//                pixelColor = tFuncFront.getColor(val);

                //BufferedImage/image/texture expects a pixel color packed as ARGB in an int
                //use the function computeImageColor to convert your double color in the range 0-1 to the format need by the image
                int packedPixelColor = computePackedPixelColor(pixelColor.r, pixelColor.g, pixelColor.b, pixelColor.a);
                image.setRGB(i, j, packedPixelColor);
            }
        }
    }

    /**
     * Do NOT modify this function.
     *
     * Updates {@link #image} attribute (result of rendering) using MIP
     * raycasting. It returns the color assigned to a ray/pixel given its
     * starting and ending points, and the direction of the ray.
     *
     * @param entryPoint Starting point of the ray.
     * @param exitPoint Last point of the ray.
     * @param rayVector Direction of the ray.
     * @param sampleStep Sample step of the ray.
     * @return Color assigned to a ray/pixel.
     */
    private int traceRayMIP(double[] entryPoint, double[] exitPoint, double[] rayVector, double sampleStep) {
        //compute the increment and the number of samples
        double[] increments = new double[3];
        VectorMath.setVector(increments, rayVector[0] * sampleStep, rayVector[1] * sampleStep, rayVector[2] * sampleStep);
//        System.out.printf("increments: %f, %f, %f\n", increments[0], increments[1], increments[2]);
        // Compute the number of times we need to sample
        double distance = VectorMath.distance(entryPoint, exitPoint);
        int nrSamples = 1 + (int) Math.floor(VectorMath.distance(entryPoint, exitPoint) / sampleStep);

        //the current position is initialized as the entry point
        double[] currentPos = new double[3];
        VectorMath.setVector(currentPos, entryPoint[0], entryPoint[1], entryPoint[2]);

        double maximum = 0;
        do {
            double value = getVoxel(currentPos) / 255.;
            if (value > maximum) {
                maximum = value;
            }
            for (int i = 0; i < 3; i++) {
                currentPos[i] += increments[i];
            }
            nrSamples--;
        } while (nrSamples > 0);

        double alpha;
        double r, g, b;
        if (maximum > 0.0) { // if the maximum = 0 make the voxel transparent
            alpha = 1.0;
        } else {
            alpha = 0.0;
        }
        r = g = b = maximum;
        int color = computePackedPixelColor(r, g, b, alpha);
        return color;
    }
    
    /**
     *
     * Updates {@link #image} attribute (result of rendering) using the
     * compositing/accumulated raycasting. It returns the color assigned to a
     * ray/pixel given its starting and ending points, and the direction of the
     * ray.
     *
     * Ray must be sampled with a distance defined by sampleStep.
     *
     * @param entryPoint Starting point of the ray.
     * @param exitPoint Last point of the ray.
     * @param rayVector Direction of the ray.
     * @param sampleStep Sample step of the ray.
     * @return Color assigned to a ray/pixel.
     */
    private int traceRayComposite(double[] entryPoint, double[] exitPoint, double[] rayVector, double sampleStep, int planeFlag) {
        double[] lightVector = new double[3];
        double[] increments = new double[3];
        
        //the light vector is directed toward the view point (which is the source of the light)
        // another light vector would be possible 
        VectorMath.setVector(lightVector, rayVector[0], rayVector[1], rayVector[2]);
        VectorMath.setVector(increments, rayVector[0] * sampleStep, rayVector[1] * sampleStep, rayVector[2] * sampleStep);
        
        double distance = VectorMath.distance(entryPoint, exitPoint);
        int nrSamples = 1 + (int) Math.floor(VectorMath.distance(entryPoint, exitPoint) / sampleStep);
        
        double[] currentPos = new double[3];
        
        // To get the correct result accumulate from exit Point since the ray is pointing from viewer to the object
        VectorMath.setVector(currentPos, entryPoint[0], entryPoint[1], entryPoint[2]);
        
        //Initialization of the colors as floating point values
        double r, g, b;
        r = g = b = 0.0;
        double alpha = 0.0;
        double opacity = 0;

        
        TFColor voxel_color = new TFColor();
        TFColor prev_color = new TFColor(0, 0, 0, 0);
        TFColor curr_color = new TFColor();
        
        RaycastMode mode;
        if (planeFlag == 1){
            mode = modeFront;
        }
        else{
            mode = modeBack;
        }
        
        
        // TODO 2: To be Implemented this function. Now, it just gives back a constant color depending on the mode
        switch (mode) {
            case COMPOSITING:
                // 1D transfer function             
                do {
                    int value = getVoxelTrilinear(currentPos);
                    if (planeFlag == 1){
                        voxel_color = tFuncFront.getColor(value);
                    }
                    else{
                        voxel_color = tFuncBack.getColor(value);
                    }
//                    System.out.printf("curr: %f, %f, %f\n", currentPos[0], currentPos[1], currentPos[2]);
                    
                    // shading for color
                    if (shadingMode){
                        VoxelGradient voxel_grad = getGradientTrilinear(currentPos);
                        voxel_color = computePhongShading(voxel_color, voxel_grad, lightVector, rayVector);
                    }
                    
                    // ray tracing with 1D tf                    
                    curr_color.r = prev_color.r + voxel_color.r * voxel_color.a * (1 - prev_color.a);
                    curr_color.g = prev_color.g + voxel_color.g * voxel_color.a * (1 - prev_color.a);
                    curr_color.b = prev_color.b + voxel_color.b * voxel_color.a * (1 - prev_color.a);
                    curr_color.a = prev_color.a + voxel_color.a * (1 - prev_color.a);
                    prev_color = curr_color;

                    for (int i = 0; i < 3; i++) {
                        currentPos[i] += increments[i];
                    }
                    nrSamples--;
                } while (nrSamples > 0);
        
                break;
                
            case TRANSFER2D:
                // 2D transfer function 
                double material_value;
                double material_r;
                TFColor set_color;
                if (planeFlag == 1){
                    material_value = tfEditor2DFront.tf2D.baseIntensity;
                    material_r = tfEditor2DFront.tf2D.radius;
                    set_color = tfEditor2DFront.tf2D.color;
                }
                else{
                    material_value = tfEditor2DBack.tf2D.baseIntensity;
                    material_r = tfEditor2DBack.tf2D.radius;
                    set_color = tfEditor2DBack.tf2D.color;
                }
                
                voxel_color.r = set_color.r;
                voxel_color.g = set_color.g;
                voxel_color.b = set_color.b;
                voxel_color.a = set_color.a;
                
                do {
                    int voxelValue = getVoxelTrilinear(currentPos);
                    VoxelGradient voxel_grad = getGradientTrilinear(currentPos);
                    
                    voxel_color.r = set_color.r;
                    voxel_color.g = set_color.g;
                    voxel_color.b = set_color.b;
                    voxel_color.a = set_color.a;
                    
                    if (shadingMode){
                        voxel_color = computePhongShading(voxel_color, voxel_grad, lightVector, rayVector);
                    }
                                    
                    voxel_color.a = computeOpacity2DTF(material_value, material_r, voxelValue, voxel_grad.mag, set_color.a);

                    curr_color.r = prev_color.r + voxel_color.r * voxel_color.a * (1 - prev_color.a);
                    curr_color.g = prev_color.g + voxel_color.g * voxel_color.a * (1 - prev_color.a);
                    curr_color.b = prev_color.b + voxel_color.b * voxel_color.a * (1 - prev_color.a);
                    curr_color.a = prev_color.a + voxel_color.a * (1 - prev_color.a);
                    prev_color = curr_color;

                    for (int i = 0; i < 3; i++) {
                        currentPos[i] += increments[i];
                    }
                    
                    
                    nrSamples--;
                } while (nrSamples > 0);
        }

        r = curr_color.r;
        g = curr_color.g;
        b = curr_color.b;
        alpha = curr_color.a;

        //computes the color
        int color = computePackedPixelColor(r, g, b, alpha);
//        System.out.printf("color=%d\n", color);
        return color;
    }

        /**
     *
     * Updates {@link #image} attribute (result of rendering) using the
     * Isosurface raycasting. It returns the color assigned to a ray/pixel given
     * its starting and ending points, and the direction of the ray.
     *
     * @param entryPoint Starting point of the ray.
     * @param exitPoint Last point of the ray.
     * @param rayVector Direction of the ray.
     * @param sampleStep Sample step of the ray.
     * @return Color assigned to a ray/pixel.
     */
    
    private int traceRayIso(double[] entryPoint, double[] exitPoint, double[] rayVector, double sampleStep, int planeFlag) {

        double[] lightVector = new double[3];
        double[] increments = new double[3];

        //the light vector is directed toward the view point (which is the source of the light)
        // another light vector would be possible 
        VectorMath.setVector(lightVector, rayVector[0], rayVector[1], rayVector[2]);
        VectorMath.setVector(increments, rayVector[0] * sampleStep, rayVector[1] * sampleStep, rayVector[2] * sampleStep);

        // TODO 3: Implement isosurface rendering.
        //Initialization of the colors as floating point values
        double r, g, b;
        r = g = b = 0.0;
        double alpha = 0.0;
        double opacity = 0;
        double[] currentPos = new double[3];
//        VectorMath.setVector(currentPos, exitPoint[0], exitPoint[1], exitPoint[2]);
        VectorMath.setVector(currentPos, entryPoint[0], entryPoint[1], entryPoint[2]);
        double distance = VectorMath.distance(entryPoint, exitPoint);
        int nrSamples = 1 + (int) Math.floor(VectorMath.distance(entryPoint, exitPoint) / sampleStep);
        
        float isoValue = 0;
        TFColor isoColor = new TFColor();
        if (planeFlag == 1){
            isoValue = this.isoValueFront;
            isoColor.r = isoColorFront.r;
            isoColor.g = isoColorFront.g;
            isoColor.b = isoColorFront.b;
        }
        else{
            isoValue = this.isoValueBack;
            isoColor.r = isoColorBack.r;
            isoColor.g = isoColorBack.g;
            isoColor.b = isoColorBack.b;
        }
        do {
            double value = getVoxelTrilinear(currentPos);
            if (value - this.isoValueFront >= eps) {
                alpha = 1;
                break;
            }
            for (int i = 0; i < 3; i++) {
//                currentPos[i] -= increments[i];
                 currentPos[i] += increments[i];
            }
            nrSamples--;
        } while (nrSamples > 0);


        // isoColorFront contains the isosurface color from the GUI
//        r = isoColorFront.r;
//        g = isoColorFront.g;
//        b = isoColorFront.b;
        r = isoColor.r;
        g = isoColor.g;
        b = isoColor.b;
        //computes the color
        int color = computePackedPixelColor(r, g, b, alpha);
        
        TFColor shaded_color = new TFColor(r, g, b, alpha);
        if(shadingMode){
            VoxelGradient voxel_grad = getGradientTrilinear(currentPos);
            shaded_color = computePhongShading(shaded_color, voxel_grad, lightVector, rayVector);
            color = computePackedPixelColor(shaded_color.r, shaded_color.g, shaded_color.b, shaded_color.a);
        }
        
        return color;
    }

    
    
    /**
     * Compute Phong Shading given the voxel color (material color), gradient,
     * light vector and view vector.
     *
     * @param voxel_color Voxel color (material color).
     * @param gradient Gradient voxel.
     * @param lightVector Light vector.
     * @param rayVector View vector.
     * @return Computed color for Phong Shading.
     */
    private TFColor computePhongShading(TFColor voxel_color, VoxelGradient gradient, double[] lightVector,
            double[] rayVector) {

        // TODO 7: Implement Phong Shading.
        TFColor color = new TFColor(voxel_color.r, voxel_color.g, voxel_color.b, voxel_color.a);
        
        double[] L = new double[3];
        double[] N = new double[3];
        double[] H = new double[3];
        double[] V = new double[3];
        
        VectorMath.setVector(L, lightVector[0], lightVector[1], lightVector[2]);
        VectorMath.setVector(V, rayVector[0], rayVector[1], rayVector[2]);
        
        double[] L_add_V = new double[3];
        VectorMath.setVector(L_add_V, L[0] + V[0], L[1] + V[1], L[2] + V[2]);
        double L_add_V_mag = Math.sqrt(L_add_V[0] * L_add_V[0] + L_add_V[1] * L_add_V[1] + L_add_V[2] * L_add_V[2]);
        
        for(int i = 0; i < 3; i++) {
            H[i] = L_add_V[i] / (L_add_V_mag + eps);
        }
        
        double L_mag = Math.sqrt(L[0] * L[0] + L[1] * L[1] + L[2] * L[2]);
        for(int i = 0; i < 3; i++) {
            L[i] /= (L_mag + eps);
        }
       
        N[0] = gradient.x / (gradient.mag + eps);
        N[1] = gradient.y / (gradient.mag + eps);
        N[2] = gradient.z / (gradient.mag + eps);

   
//        double L_dot_N = Math.max(VectorMath.dotproduct(L, N), 0.0);
//        double N_dot_H = Math.max(VectorMath.dotproduct(N, H), 0.0);
        double L_dot_N = Math.abs(VectorMath.dotproduct(L, N));
        double N_dot_H = Math.abs(VectorMath.dotproduct(N, H));

        
        // our slide, simplified formula - I_a ?
//        color.r = k_a + voxel_color.r * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);
//        color.g = k_a + voxel_color.g * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);
//        color.b = k_a + voxel_color.b * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);

        // Our slide, I_a as voxel_color, incorrect result
//        color.r = voxel_color.r + voxel_color.r * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);
//        color.g = voxel_color.g + voxel_color.g * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);
//        color.b = voxel_color.b + voxel_color.b * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha); 

//        // modify I_a
        color.r = voxel_color.r * k_a + voxel_color.r * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);
        color.g = voxel_color.g * k_a + voxel_color.g * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);
        color.b = voxel_color.b * k_a + voxel_color.b * k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha);

//        color.r = voxel_color.r * (k_a + k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha));
//        color.g = voxel_color.g * (k_a + k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha));
//        color.b = voxel_color.b * (k_a + k_d * L_dot_N + k_s * Math.pow(N_dot_H, hl_alpha));

//        // KTH slide
//        color.r = voxel_color.r * (k_a + k_d * L_dot_N) + k_s * Math.pow(N_dot_H, hl_alpha);
//        color.g = voxel_color.g * (k_a + k_d * L_dot_N) + k_s * Math.pow(N_dot_H, hl_alpha);
//        color.b = voxel_color.b * (k_a + k_d * L_dot_N) + k_s * Math.pow(N_dot_H, hl_alpha);
        

//        System.out.printf("%f, %f, %f\n", voxel_color.r, voxel_color.g, voxel_color.b); // (1.0, 1.0, 0)
//        System.out.printf("%f, %f, %f\n", gradient.x, gradient.y, gradient.z);
//        System.out.printf("%f, %f, %f\n", color.r, color.g, color.b);
        color.a = voxel_color.a;

        return color;
    }
    
    

    /**
     * Implements the basic tracing of rays through the image given the camera
     * transformation. It calls the functions depending on the raycasting mode.
     *
     * @param viewMatrix
     */
    void raycast(double[] viewMatrix) {
        //data allocation
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        double[] pixelCoord = new double[3];
        double[] entryPoint = new double[3];
        double[] exitPoint = new double[3];

        // TODO 5: Limited modification is needed
        // increment in the pixel domain in pixel units
        int increment = 1;
        // sample step in voxel units
        int sampleStep = 1;
        if(this.interactiveMode){
//            increment = 4;
            sampleStep = 5;
        }

        // reset the image to black
        resetImage();

        // vector uVec and vVec define a plane through the origin,
        // perpendicular to the view vector viewVec which is going from the view point towards the object
        // uVec contains the up vector of the camera in world coordinates (image vertical)
        // vVec contains the horizontal vector in world coordinates (image horizontal)
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // We get the size of the image/texture we will be puting the result of the 
        // volume rendering operation.
        int imageW = image.getWidth();
        int imageH = image.getHeight();

        int[] imageCenter = new int[2];
        // Center of the image/texture 
        imageCenter[0] = imageW / 2;
        imageCenter[1] = imageH / 2;

        //The rayVector is pointing towards the scene
        double[] rayVector = new double[3];
        rayVector[0] = -viewVec[0];
        rayVector[1] = -viewVec[1];
        rayVector[2] = -viewVec[2];

        // compute the volume center
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // ray computation for each pixel
        for (int j = imageCenter[1] - imageH / 2; j < imageCenter[1] + imageH / 2; j += increment) {
            for (int i = imageCenter[0] - imageW / 2; i < imageCenter[0] + imageW / 2; i += increment) {
                // compute starting points of rays in a plane shifted backwards to a position behind the data set
                computePixelCoordinatesBehindFloat(pixelCoord, viewVec, uVec, vVec, i, j);
                // compute the entry and exit point of the ray
                computeEntryAndExit(pixelCoord, rayVector, entryPoint, exitPoint);

                // TODO 9: Implement logic for cutting plane.
                if ((entryPoint[0] > -1.0) && (exitPoint[0] > -1.0)) {
                    int val = 0;

                    double[] dir_vec = new double[3];
                    double dir = 0;
                    int planeFlag = 0;
                    for (int k = 0; k < 3; k++){
                        dir_vec[k] = entryPoint[k] - planePoint[k];
                    }
                    dir = 0;
                    for (int k = 0; k < 3; k++){
                        dir += dir_vec[k] * planeNorm[k];
                    }
                    if (cuttingPlaneMode){
                        if (dir >= 0){
                            planeFlag = 1;
                            switch (modeFront) {
                                case COMPOSITING:
                                case TRANSFER2D:
                                    val = traceRayComposite(entryPoint, exitPoint, rayVector, sampleStep, planeFlag);
                                    break;
                                case MIP:
                                    val = traceRayMIP(entryPoint, exitPoint, rayVector, sampleStep);
                                    break;
                                case ISO_SURFACE:
                                    val = traceRayIso(entryPoint, exitPoint, rayVector, sampleStep, planeFlag);
                                    break;
                            }
                            for (int ii = i; ii < i + increment; ii++) {
                                for (int jj = j; jj < j + increment; jj++) {
                                    image.setRGB(ii, jj, val);
                                }
                            }
                        }
                        else{
                            planeFlag = -1;
                            switch (modeBack) {
                                case COMPOSITING:
                                case TRANSFER2D:
                                    val = traceRayComposite(entryPoint, exitPoint, rayVector, sampleStep, planeFlag);
                                    break;
                                case MIP:
                                    val = traceRayMIP(entryPoint, exitPoint, rayVector, sampleStep);
                                    break;
                                case ISO_SURFACE:
                                    val = traceRayIso(entryPoint, exitPoint, rayVector, sampleStep, planeFlag);
                                    break;
                            }
                            for (int ii = i; ii < i + increment; ii++) {
                                for (int jj = j; jj < j + increment; jj++) {
                                    image.setRGB(ii, jj, val);
                                }
                            }
                        }
                    }
                    else{
                        planeFlag = 1;
                        switch (modeFront) {
                                case COMPOSITING:
                                case TRANSFER2D:
                                    val = traceRayComposite(entryPoint, exitPoint, rayVector, sampleStep, planeFlag);
                                    break;
                                case MIP:
                                    val = traceRayMIP(entryPoint, exitPoint, rayVector, sampleStep);
                                    break;
                                case ISO_SURFACE:
                                    val = traceRayIso(entryPoint, exitPoint, rayVector, sampleStep, planeFlag);
                                    break;
                            }
                            for (int ii = i; ii < i + increment; ii++) {
                                for (int jj = j; jj < j + increment; jj++) {
                                    image.setRGB(ii, jj, val);
                                }
                            }
                    }
                
                
                
                
                
                }

            }
        }
    }

    /**
     * Computes the opacity based on the value of the pixel and values of the
     * triangle widget. {@link #tFunc2DFront} contains the values of the base
     * intensity and radius. {@link TransferFunction2D#baseIntensity} and
     * {@link TransferFunction2D#radius} are in image intensity units.
     *
     * @param material_value Value of the material.
     * @param material_r Radius of the material.
     * @param voxelValue Voxel value.
     * @param gradMagnitude Gradient magnitude.
     * @return
     */
    public double computeOpacity2DTF(double material_value, double material_r,
            double voxelValue, double gradMagnitude, double set_opacity) {

        double opacity = 0.0;
        double radius = material_r / gradients.getMaxGradientMagnitude();
//        double set_opacity = tfEditor2DFront.tf2D.color.a;
         
        // TODO 8: Implement weight based opacity.
//        if ((Math.abs(gradMagnitude - 0) <= eps) && 
//                (Math.abs(voxelValue - material_value) <= eps)){
//            opacity = set_opacity;
//        }
//        else if (Math.abs(gradMagnitude) > eps && 
//                (voxelValue - radius * Math.abs(gradMagnitude) - material_value <= eps) && 
//                (material_value - voxelValue - radius * Math.abs(gradMagnitude) <= eps)){
//            opacity = set_opacity * (1 - 1 / radius * Math.abs((material_value - voxelValue) / gradMagnitude));
//        }
//        else {
//            opacity = 0;
//        }
        
        if (gradMagnitude == 0 && voxelValue == material_value) {
            opacity = set_opacity;
        }
        else if (gradMagnitude > 0 && (voxelValue - radius * gradMagnitude <= material_value) &&
                 (voxelValue + radius * gradMagnitude >= material_value)){
            opacity = set_opacity * (1 - 1 / radius * Math.abs((material_value - voxelValue) / gradMagnitude));
        }
        else {
            opacity = 0;
        }

        return opacity;
    }

    /**
     * Class constructor. Initializes attributes.
     */
    public RaycastRenderer() {
        panelFront = new RaycastRendererPanel(this);
        panelFront.setSpeedLabel("0");

        isoColorFront = new TFColor();
        isoColorFront.r = 1.0;
        isoColorFront.g = 1.0;
        isoColorFront.b = 0.0;
        isoColorFront.a = 1.0;

        isoColorBack = new TFColor();
        isoColorBack.r = 1.0;
        isoColorBack.g = 1.0;
        isoColorBack.b = 0.0;
        isoColorBack.a = 1.0;

        modeFront = RaycastMode.SLICER;
        modeBack = RaycastMode.SLICER;
    }

    /**
     * Sets the volume to be visualized. It creates the Image buffer for the
     * size of the volume. Initializes the transfers functions
     *
     * @param vol Volume to be visualized.
     */
    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }

        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);

        // Initialize transfer function and GUI panels
        tFuncFront = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        tFuncFront.setTestFunc();
        tFuncFront.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFuncFront, volume.getHistogram());

        tFunc2DFront = new TransferFunction2D((short) (volume.getMaximum() / 2), 0.2 * volume.getMaximum());
        tfEditor2DFront = new TransferFunction2DEditor(tFunc2DFront, volume, gradients);
        tfEditor2DFront.addTFChangeListener(this);

        // Initialize transfer function and GUI panels for cutting plane
        tFuncBack = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        tFuncBack.setTestFunc();
        tFuncBack.addTFChangeListener(this);
        tfEditorBack = new TransferFunctionEditor(tFuncBack, volume.getHistogram());

        tFunc2DBack = new TransferFunction2D((short) (volume.getMaximum() / 2), 0.2 * volume.getMaximum());
        tfEditor2DBack = new TransferFunction2DEditor(tFunc2DBack, volume, gradients);
        tfEditor2DBack.addTFChangeListener(this);

        // Set plane point
        VectorMath.setVector(planePoint, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    /**
     * Do NOT modify.
     *
     * Visualizes the volume. It calls the corresponding render functions.
     *
     * @param gl OpenGL API.
     */
    @Override
    public void visualize(GL2 gl) {
        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        // If mode is Cutting Plane, draw the cutting plane.
        if (cuttingPlaneMode) {
            drawCuttingPlane(gl);
        }

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, _viewMatrix, 0);

        long startTime = System.currentTimeMillis();

        switch (modeFront) {
            case SLICER:
                slicer(_viewMatrix);
                break;
            default:
                // Default case raycast
                raycast(_viewMatrix);
                break;
        }

        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panelFront.setSpeedLabel(Double.toString(runningTime));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2.0;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();

        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }
    }

    public RaycastMode getRaycastMode() {
        return modeFront;
    }

    /**
     * Sets the raycast mode to the specified one.
     *
     * @param mode New Raycast mode.
     */
    public void setRaycastModeFront(RaycastMode mode) {
        this.modeFront = mode;
    }

    public void setRaycastModeBack(RaycastMode mode) {
        this.modeBack = mode;
    }

    @Override
    public void changed() {
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }

    /**
     * Do NOT modify.
     *
     * Updates the vectors that represent the cutting plane.
     *
     * @param d View Matrix.
     */
    public void updateCuttingPlaneVectors(double[] d) {
        VectorMath.setVector(_planeU, d[1], d[5], d[9]);
        VectorMath.setVector(_planeV, d[2], d[6], d[10]);
        VectorMath.setVector(planeNorm, d[0], d[4], d[8]);
    }

    /**
     * Sets the cutting plane mode flag.
     *
     * @param cuttingPlaneMode
     */
    public void setCuttingPlaneMode(boolean cuttingPlaneMode) {
        this.cuttingPlaneMode = cuttingPlaneMode;
    }

    public boolean isCuttingPlaneMode() {
        return cuttingPlaneMode;
    }

    /**
     * Sets shading mode flag.
     *
     * @param shadingMode
     */
    public void setShadingMode(boolean shadingMode) {
        this.shadingMode = shadingMode;
    }

    public RaycastRendererPanel getPanel() {
        return panelFront;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2DFront;
    }

    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }

    public TransferFunction2DEditor getTF2DPanelBack() {
        return tfEditor2DBack;
    }

    public TransferFunctionEditor getTFPanelBack() {
        return tfEditorBack;
    }

    //////////////////////////////////////////////////////////////////////
    /////////////////// PRIVATE FUNCTIONS AND ATTRIBUTES /////////////////
    //////////////////////////////////////////////////////////////////////
    /**
     * OpenGL View Matrix. The shape (4x4) remains constant.
     */
    private final double[] _viewMatrix = new double[4 * 4];

    /**
     * Vector used to draw the cutting plane.
     */
    private final double[] _planeU = new double[3];

    /**
     * Vector used to draw the cutting plane.
     */
    private final double[] _planeV = new double[3];

    /**
     * Do NOT modify.
     *
     * Draws the bounding box around the volume.
     *
     * @param gl OpenGL API.
     */
    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();
    }

    /**
     * Do NOT modify.
     *
     * Draws the cutting plane through.
     *
     * @param gl OpenGL API.
     */
    private void drawCuttingPlane(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(2f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        double D = Math.sqrt(Math.pow(volume.getDimX(), 2) + Math.pow(volume.getDimY(), 2) + Math.pow(volume.getDimZ(), 2)) / 2;

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-_planeU[0] * D - _planeV[0] * D, -_planeU[1] * D - _planeV[1] * D, -_planeU[2] * D - _planeV[2] * D);
        gl.glVertex3d(_planeU[0] * D - _planeV[0] * D, _planeU[1] * D - _planeV[1] * D, _planeU[2] * D - _planeV[2] * D);
        gl.glVertex3d(_planeU[0] * D + _planeV[0] * D, _planeU[1] * D + _planeV[1] * D, _planeU[2] * D + _planeV[2] * D);
        gl.glVertex3d(-_planeU[0] * D + _planeV[0] * D, -_planeU[1] * D + _planeV[1] * D, -_planeU[2] * D + _planeV[2] * D);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();
    }

    /**
     * Do NOT modify this function.
     *
     * Sets the Iso value.
     *
     * @param newColor
     */
    public void setIsoValueFront(float isoValueFront) {
        this.isoValueFront = isoValueFront;
    }

    /**
     * Do NOT modify this function.
     *
     * Sets the Iso value.
     *
     * @param newColor
     */
    public void setIsoValueBack(float isoValueBack) {
        this.isoValueBack = isoValueBack;
    }

    /**
     * Do NOT modify this function.
     *
     * Sets the Iso Color.
     *
     * @param newColor
     */
    public void setIsoColorFront(TFColor newColor) {
        this.isoColorFront.r = newColor.r;
        this.isoColorFront.g = newColor.g;
        this.isoColorFront.b = newColor.b;
    }

    /**
     * Do NOT modify this function.
     *
     * Sets the Iso Color.
     *
     * @param newColor
     */
    public void setIsoColorBack(TFColor newColor) {
        this.isoColorBack.r = newColor.r;
        this.isoColorBack.g = newColor.g;
        this.isoColorBack.b = newColor.b;
    }

    /**
     * Do NOT modify this function.
     *
     * Resets the image with 0 values.
     */
    private void resetImage() {
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                image.setRGB(i, j, 0);
            }
        }
    }

    /**
     * Do NOT modify this function.
     *
     * Computes the increments according to sample step and stores the result in
     * increments.
     *
     * @param increments Vector to store the result.
     * @param rayVector Ray vector.
     * @param sampleStep Sample step.
     */
    private void computeIncrementsB2F(double[] increments, double[] rayVector, double sampleStep) {
        // we compute a back to front compositing so we start increments in the oposite direction than the pixel ray
        VectorMath.setVector(increments, -rayVector[0] * sampleStep, -rayVector[1] * sampleStep, -rayVector[2] * sampleStep);
    }

    /**
     * Do NOT modify this function.
     *
     * Packs a color into a Integer.
     *
     * @param r Red component of the color.
     * @param g Green component of the color.
     * @param b Blue component of the color.
     * @param a Alpha component of the color.
     * @return
     */
    private static int computePackedPixelColor(double r, double g, double b, double a) {
        int c_alpha = a <= 1.0 ? (int) Math.floor(a * 255) : 255;
        int c_red = r <= 1.0 ? (int) Math.floor(r * 255) : 255;
        int c_green = g <= 1.0 ? (int) Math.floor(g * 255) : 255;
        int c_blue = b <= 1.0 ? (int) Math.floor(b * 255) : 255;
        int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
        return pixelColor;
    }

    /**
     * Do NOT modify this function.
     *
     * Computes the entry and exit of a view vector with respect the faces of
     * the volume.
     *
     * @param p Point of the ray.
     * @param viewVec Direction of the ray.
     * @param entryPoint Vector to store entry point.
     * @param exitPoint Vector to store exit point.
     */
    private void computeEntryAndExit(double[] p, double[] viewVec, double[] entryPoint, double[] exitPoint) {

        for (int i = 0; i < 3; i++) {
            entryPoint[i] = -1;
            exitPoint[i] = -1;
        }

        double[] plane_pos = new double[3];
        double[] plane_normal = new double[3];
        double[] intersection = new double[3];

        VectorMath.setVector(plane_pos, volume.getDimX(), 0, 0);
        VectorMath.setVector(plane_normal, 1, 0, 0);
        intersectFace(plane_pos, plane_normal, p, viewVec, intersection, entryPoint, exitPoint);

        VectorMath.setVector(plane_pos, 0, 0, 0);
        VectorMath.setVector(plane_normal, -1, 0, 0);
        intersectFace(plane_pos, plane_normal, p, viewVec, intersection, entryPoint, exitPoint);

        VectorMath.setVector(plane_pos, 0, volume.getDimY(), 0);
        VectorMath.setVector(plane_normal, 0, 1, 0);
        intersectFace(plane_pos, plane_normal, p, viewVec, intersection, entryPoint, exitPoint);

        VectorMath.setVector(plane_pos, 0, 0, 0);
        VectorMath.setVector(plane_normal, 0, -1, 0);
        intersectFace(plane_pos, plane_normal, p, viewVec, intersection, entryPoint, exitPoint);

        VectorMath.setVector(plane_pos, 0, 0, volume.getDimZ());
        VectorMath.setVector(plane_normal, 0, 0, 1);
        intersectFace(plane_pos, plane_normal, p, viewVec, intersection, entryPoint, exitPoint);

        VectorMath.setVector(plane_pos, 0, 0, 0);
        VectorMath.setVector(plane_normal, 0, 0, -1);
        intersectFace(plane_pos, plane_normal, p, viewVec, intersection, entryPoint, exitPoint);
    }

    /**
     * Do NOT modify this function.
     *
     * Checks if a line intersects a plane.
     *
     * @param plane_pos Position of plane.
     * @param plane_normal Normal of plane.
     * @param line_pos Position of line.
     * @param line_dir Direction of line.
     * @param intersection Vector to store intersection.
     * @return True if intersection happens. False otherwise.
     */
    private static boolean intersectLinePlane(double[] plane_pos, double[] plane_normal,
            double[] line_pos, double[] line_dir, double[] intersection) {

        double[] tmp = new double[3];

        for (int i = 0; i < 3; i++) {
            tmp[i] = plane_pos[i] - line_pos[i];
        }

        double denom = VectorMath.dotproduct(line_dir, plane_normal);
        if (Math.abs(denom) < 1.0e-8) {
            return false;
        }

        double t = VectorMath.dotproduct(tmp, plane_normal) / denom;

        for (int i = 0; i < 3; i++) {
            intersection[i] = line_pos[i] + t * line_dir[i];
        }

        return true;
    }

    /**
     * Do NOT modify this function.
     *
     * Checks if it is a valid intersection.
     *
     * @param intersection Vector with the intersection point.
     * @param xb
     * @param xe
     * @param yb
     * @param ye
     * @param zb
     * @param ze
     * @return
     */
    private static boolean validIntersection(double[] intersection, double xb, double xe, double yb,
            double ye, double zb, double ze) {

        return (((xb - 0.5) <= intersection[0]) && (intersection[0] <= (xe + 0.5))
                && ((yb - 0.5) <= intersection[1]) && (intersection[1] <= (ye + 0.5))
                && ((zb - 0.5) <= intersection[2]) && (intersection[2] <= (ze + 0.5)));

    }

    /**
     * Do NOT modify this function.
     *
     * Checks the intersection of a line with a plane and returns entry and exit
     * points in case intersection happens.
     *
     * @param plane_pos Position of plane.
     * @param plane_normal Normal vector of plane.
     * @param line_pos Position of line.
     * @param line_dir Direction of line.
     * @param intersection Vector to store the intersection point.
     * @param entryPoint Vector to store the entry point.
     * @param exitPoint Vector to store the exit point.
     */
    private void intersectFace(double[] plane_pos, double[] plane_normal,
            double[] line_pos, double[] line_dir, double[] intersection,
            double[] entryPoint, double[] exitPoint) {

        boolean intersect = intersectLinePlane(plane_pos, plane_normal, line_pos, line_dir,
                intersection);
        if (intersect) {

            double xpos0 = 0;
            double xpos1 = volume.getDimX();
            double ypos0 = 0;
            double ypos1 = volume.getDimY();
            double zpos0 = 0;
            double zpos1 = volume.getDimZ();

            if (validIntersection(intersection, xpos0, xpos1, ypos0, ypos1,
                    zpos0, zpos1)) {
                if (VectorMath.dotproduct(line_dir, plane_normal) < 0) {
                    entryPoint[0] = intersection[0];
                    entryPoint[1] = intersection[1];
                    entryPoint[2] = intersection[2];
                } else {
                    exitPoint[0] = intersection[0];
                    exitPoint[1] = intersection[1];
                    exitPoint[2] = intersection[2];
                }
            }
        }
    }

    /**
     * Do NOT modify this function.
     *
     * Calculates the pixel coordinate for the given parameters.
     *
     * @param pixelCoord Vector to store the result.
     * @param volumeCenter Location of the center of the volume.
     * @param uVec uVector.
     * @param vVec vVector.
     * @param i Pixel i.
     * @param j Pixel j.
     */
    private void computePixelCoordinatesFloat(double pixelCoord[], double volumeCenter[], double uVec[], double vVec[], float i, float j) {
        // Coordinates of a plane centered at the center of the volume (volumeCenter and oriented according to the plane defined by uVec and vVec
        float imageCenter = image.getWidth() / 2;
        pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter) + volumeCenter[0];
        pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter) + volumeCenter[1];
        pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter) + volumeCenter[2];
    }

    /**
     * Do NOT modify this function.
     *
     * Same as
     * {@link RaycastRenderer#computePixelCoordinatesFloat(double[], double[], double[], double[], float, float)}
     * but for integer pixel coordinates.
     *
     * @param pixelCoord Vector to store the result.
     * @param volumeCenter Location of the center of the volume.
     * @param uVec uVector.
     * @param vVec vVector.
     * @param i Pixel i.
     * @param j Pixel j.
     */
    private void computePixelCoordinates(double pixelCoord[], double volumeCenter[], double uVec[], double vVec[], int i, int j) {
        // Coordinates of a plane centered at the center of the volume (volumeCenter and oriented according to the plane defined by uVec and vVec
        int imageCenter = image.getWidth() / 2;
        pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter) + volumeCenter[0];
        pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter) + volumeCenter[1];
        pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter) + volumeCenter[2];
    }

    /**
     * Do NOT modify this function.
     *
     * Calculates the pixel coordinate for the given parameters. It calculates
     * the coordinate having the center (0,0) of the view plane aligned with the
     * center of the volume and moved a distance equivalent to the diagonal to
     * make sure we are far enough.
     *
     * @param pixelCoord Vector to store the result.
     * @param viewVec View vector (ray).
     * @param uVec uVector.
     * @param vVec vVector.
     * @param i Pixel i.
     * @param j Pixel j.
     */
    private void computePixelCoordinatesBehindFloat(double pixelCoord[], double viewVec[], double uVec[], double vVec[], float i, float j) {
        int imageCenter = image.getWidth() / 2;
        // Pixel coordinate is calculate having the center (0,0) of the view plane aligned with the center of the volume and moved a distance equivalent
        // to the diaganal to make sure I am far away enough.

        double diagonal = Math.sqrt((volume.getDimX() * volume.getDimX()) + (volume.getDimY() * volume.getDimY()) + (volume.getDimZ() * volume.getDimZ())) / 2;
        pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter) + viewVec[0] * diagonal + volume.getDimX() / 2.0;
        pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter) + viewVec[1] * diagonal + volume.getDimY() / 2.0;
        pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter) + viewVec[2] * diagonal + volume.getDimZ() / 2.0;
    }

    /**
     * Do NOT modify this function.
     *
     * Same as
     * {@link RaycastRenderer#computePixelCoordinatesBehindFloat(double[], double[], double[], double[], int, int)}
     * but for integer pixel coordinates.
     *
     * @param pixelCoord Vector to store the result.
     * @param viewVec View vector (ray).
     * @param uVec uVector.
     * @param vVec vVector.
     * @param i Pixel i.
     * @param j Pixel j.
     */
    private void computePixelCoordinatesBehind(double pixelCoord[], double viewVec[], double uVec[], double vVec[], int i, int j) {
        int imageCenter = image.getWidth() / 2;
        // Pixel coordinate is calculate having the center (0,0) of the view plane aligned with the center of the volume and moved a distance equivalent
        // to the diaganal to make sure I am far away enough.

        double diagonal = Math.sqrt((volume.getDimX() * volume.getDimX()) + (volume.getDimY() * volume.getDimY()) + (volume.getDimZ() * volume.getDimZ())) / 2;
        pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter) + viewVec[0] * diagonal + volume.getDimX() / 2.0;
        pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter) + viewVec[1] * diagonal + volume.getDimY() / 2.0;
        pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter) + viewVec[2] * diagonal + volume.getDimZ() / 2.0;
    }
}
