/*
 * MIT License
 *
 * Copyright (c) 2018 emufog contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package emufog.docker;

/**
 * This docker image represents a host device connected to an edge router.
 * By using the scaling factor you can simulate a higher load of multiple devices.
 */
public class DeviceType extends DockerType {

    /* scaling factor of the docker image to simulate multiple hosts */
    public final int scalingFactor;

    /* average devices connected to an edge router */
    public final float averageDeviceCount;

    /**
     * Creates a new device docker instance based on the abstract DockerType.
     * Can be used to simulate multiple devices by the scaling factor.
     * Will be distributed based on the average count per router.
     *
     * @param dockerImage        actual docker image to deploy
     * @param scalingFactor      scaling factor of this docker image, factor >= 1
     * @param averageDeviceCount average number of devices of this image deployed to each router
     * @param memoryLimit        upper limit of memory to use in Bytes
     * @param cpuShare           of the sum of available computing resources
     * @throws IllegalArgumentException the docker image name cannot be null and must
     *                                  match the pattern of a docker container name
     */
    public DeviceType(String dockerImage, int scalingFactor, float averageDeviceCount, int memoryLimit, float cpuShare) throws IllegalArgumentException {
        super(dockerImage, memoryLimit, cpuShare);

        this.scalingFactor = scalingFactor;
        this.averageDeviceCount = averageDeviceCount;
    }
}
