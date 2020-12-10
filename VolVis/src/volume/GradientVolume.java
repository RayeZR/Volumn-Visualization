/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volume;

/**
 *
 * @author michel
 */
public class GradientVolume {

    public GradientVolume(Volume vol) {
        volume = vol;
        dimX = vol.getDimX();
        dimY = vol.getDimY();
        dimZ = vol.getDimZ();
        data = new VoxelGradient[dimX * dimY * dimZ];
        compute();
        maxmag = -1.0;
    }

    public VoxelGradient getGradient(int x, int y, int z) {
        return data[x + dimX * (y + dimY * z)];
    }
    
    public float[] getGradientVec(int x, int y, int z) {
        // A function to return the gradient of a voxel as a three-dimensional vector.
        float[] vec = new float[3];
        vec[0] = this.getGradient(x, y, z).x;
        vec[1] = this.getGradient(x, y, z).y;
        vec[2] = this.getGradient(x, y, z).z;
        return vec;
    }

    public void setGradient(int x, int y, int z, VoxelGradient value) {
        data[x + dimX * (y + dimY * z)] = value;
    }

    public void setVoxel(int i, VoxelGradient value) {
        data[i] = value;
    }

    public VoxelGradient getVoxel(int i) {
        return data[i];
    }

    public int getDimX() {
        return dimX;
    }

    public int getDimY() {
        return dimY;
    }

    public int getDimZ() {
        return dimZ;
    }

    /**
     * Computes the gradient information of the volume according to Levoy's
     * paper.
     */
    private void compute() {
        // TODO 4: Implement gradient computation.
        // this just initializes all gradients to the vector (0,0,0)
        for (int i = 0; i < data.length; i++) {
            data[i] = zero;
        }
        // Start from coordinates(1, 1, 1) to compute the gradient (dx, dy, dz) by taking the central difference
        // of each direction.
        // The boundary gradients are left to be (0, 0, 0).
        for (int i = 1; i < dimX - 1; i++){
            for (int j = 1; j < dimY - 1; j++){
                for (int k = 1; k < dimZ - 1; k ++){
                    float dx = (float) 0.5 * (volume.getVoxel(i + 1, j, k) - volume.getVoxel(i - 1, j, k));
                    float dy = (float) 0.5 * (volume.getVoxel(i, j + 1, k) - volume.getVoxel(i, j - 1, k));
                    float dz = (float) 0.5 * (volume.getVoxel(i, j, k + 1) - volume.getVoxel(i, j, k - 1));
                    setGradient(i, j, k, new VoxelGradient(dx, dy, dz)); 
                }
            }
        }

    }

    public double getMaxGradientMagnitude() {
        if (maxmag >= 0) {
            return maxmag;
        } else {
            double magnitude = data[0].mag;
            for (int i = 0; i < data.length; i++) {
                magnitude = data[i].mag > magnitude ? data[i].mag : magnitude;
            }
            maxmag = magnitude;
            return magnitude;
        }
    }

    private int dimX, dimY, dimZ;
    private VoxelGradient zero = new VoxelGradient();
    VoxelGradient[] data;
    Volume volume;
    double maxmag;
}
