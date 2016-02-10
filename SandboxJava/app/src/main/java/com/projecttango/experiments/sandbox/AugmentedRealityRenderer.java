/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.projecttango.experiments.sandbox;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.MotionEvent;

import com.google.atap.tangoservice.TangoPoseData;

import org.rajawali3d.Object3D;
import org.rajawali3d.cameras.Camera;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.MathUtil;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.Cube;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.util.ObjectColorPicker;
import org.rajawali3d.util.OnObjectPickedListener;

import com.projecttango.rajawali.DeviceExtrinsics;
import com.projecttango.rajawali.Pose;
import com.projecttango.rajawali.ScenePoseCalculator;
import com.projecttango.rajawali.ar.TangoRajawaliRenderer;

import java.util.Random;

/**
 * Very simple example augmented reality renderer which displays a cube fixed in place.
 * Whenever the user clicks on the screen, the cube is placed flush with the surface detected
 * with the depth camera in the position clicked.
 *
 * This follows the same development model than any regular Rajawali application
 * with the following peculiarities:
 * - It extends <code>TangoRajawaliArRenderer</code>.
 * - It calls <code>super.initScene()</code> in the initialization.
 * - When an updated pose for the object is obtained after a user click, the object pose is updated
 *   in the render loop
 * - The associated AugmentedRealityActivity is taking care of updating the camera pose to match
 *   the displayed RGB camera texture and produce the AR effect through a Scene Frame Callback
 *   (@see AugmentedRealityActivity)
 */
public class AugmentedRealityRenderer extends TangoRajawaliRenderer
        implements OnObjectPickedListener {
    private static final String TAG = "AugmentedRealityRenderer";
    private static final float CUBE_SIDE_LENGTH = 0.5f;
    private static final int SPHERE_DIVISIONS = 20;
    private static final float SPHERE_RADIUS = 0.25f;

    private ObjectColorPicker mPicker;
    private Object3D mPickedObject = null;
    private Quaternion mPickedObjectOrientation = null;
    private PoseTracker mLastPose;
    private Random mRandom;

    private static final int[] OBJECT_COLORS =
    {Color.BLUE, Color.CYAN, Color.GREEN, Color.MAGENTA, Color.RED, Color.YELLOW};

    public AugmentedRealityRenderer(Context context) {
        super(context);
    }

    @Override
    protected void initScene() {
        // Remember to call super.initScene() to allow TangoRajawaliArRenderer
        // to be set-up.
        super.initScene();
        mRandom = new Random();

        // Add a directional light in an arbitrary direction.
        DirectionalLight light = new DirectionalLight(1, 0.2, -1);
        light.setColor(1, 1, 1);
        light.setPower(0.8f);
        light.setPosition(3, 2, 4);
        getCurrentScene().addLight(light);

        mPicker = new ObjectColorPicker(this);
        mPicker.setOnObjectPickedListener(this);

        mLastPose = new PoseTracker(getCurrentCamera().getPosition(),
                getCurrentCamera().getOrientation());
    }

    @Override
    protected void onRender(long elapsedRealTime, double deltaTime) {
        super.onRender(elapsedRealTime, deltaTime);
    }

    /**
     * Update the scene camera based on the provided pose in Tango start of service frame.
     * The device pose should match the pose of the device at the time the last rendered RGB
     * frame, which can be retrieved with this.getTimestamp();
     *
     * NOTE: This must be called from the OpenGL render thread - it is not thread safe.
     */
    public void updateRenderCameraPose(TangoPoseData devicePose, DeviceExtrinsics extrinsics) {
        // update previous camera pose in the end of render cycle
        mLastPose.setOrientation(getCurrentCamera().getOrientation());
        mLastPose.setPosition(getCurrentCamera().getPosition());

        Pose currentPose = ScenePoseCalculator.toOpenGlCameraPose(devicePose, extrinsics);
        getCurrentCamera().setRotation(currentPose.getOrientation());
        getCurrentCamera().setPosition(currentPose.getPosition());
    }

    @Override
    public void onOffsetsChanged(float xOffset, float yOffset,
                                 float xOffsetStep, float yOffsetStep,
                                 int xPixelOffset, int yPixelOffset) {
    }

    @Override
    public void onTouchEvent(MotionEvent event) {}

    @Override
    public void onObjectPicked(Object3D object) {
        mPickedObject = object;
        mPickedObjectOrientation = object.getOrientation().clone();
        mPickedObjectOrientation.multiply(mLastPose.getOrientation().clone().inverse());
        Log.d(TAG, "Object picked!");
    }

    public void pickObject(float x, float y) {
        mPicker.getObjectAt(x, y);
    }

    public void unpickObject() {
        mPickedObject = null;
    }

    public Object3D getPickedObject() {
        return mPickedObject;
    }

    public PoseTracker getLastPose() {
        return mLastPose;
    }

    public void addObject() {
        Object3D object = (mRandom.nextInt(1) % 2 == 0) ? new Cube(CUBE_SIDE_LENGTH)
            : new Sphere(SPHERE_RADIUS, SPHERE_DIVISIONS, SPHERE_DIVISIONS);
        Material material = new Material();
        material.setColor(OBJECT_COLORS[mRandom.nextInt(OBJECT_COLORS.length)]);
        material.setColorInfluence(0.1f);
        material.enableLighting(true);
        material.setDiffuseMethod(new DiffuseMethod.Lambert());
        object.setMaterial(material);
        object.setPosition(getCurrentCamera().getPosition());
        object.setOrientation(getCurrentCamera().getOrientation());
        object.moveForward(-1.0f);
        mPicker.registerObject(object);
        getCurrentScene().addChild(object);
    }

    public synchronized void movePickedObject() {
        if (mPickedObject != null) {
            // move object to where you're looking
            Vector3 objectPosition = Vector3.subtractAndCreate(
                    mPickedObject.getPosition(), mLastPose.getPosition());
            Vector3 initialForward = new Vector3(0.0, 0.0, 1.0);
            initialForward.normalize();
            initialForward.rotateBy(mLastPose.getOrientation().clone());
            Vector3 finalForward = new Vector3(0.0, 0.0, 1.0);
            finalForward.normalize();
            finalForward.rotateBy(getCurrentCamera().getOrientation().clone());

            Quaternion rotation = new Quaternion();
            rotation.fromRotationBetween(finalForward, initialForward);

            Vector3 lookAt = new Vector3(0.0, 0.0, 1.0);
            lookAt.rotateBy(mPickedObject.getOrientation().clone());
            lookAt.add(objectPosition);
            objectPosition.rotateBy(rotation.clone());
            lookAt.rotateBy(rotation.clone());

            // TODO(kirmani): Set object rotation/orientation relative to camera orientation
            // set updated object's rotation and positiona
            mPickedObject.setPosition(getCurrentCamera().getPosition().clone().add(objectPosition));
            mPickedObject.setLookAt(getCurrentCamera().getPosition().clone().add(lookAt));
        }
    }
}
