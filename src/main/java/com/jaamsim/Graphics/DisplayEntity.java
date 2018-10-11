/*
 * JaamSim Discrete Event Simulation
 * Copyright (C) 2002-2011 Ausenco Engineering Canada Inc.
 * Copyright (C) 2017-2018 JaamSim Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jaamsim.Graphics;

import java.util.ArrayList;
import java.util.HashMap;

import com.jaamsim.Commands.KeywordCommand;
import com.jaamsim.DisplayModels.DisplayModel;
import com.jaamsim.DisplayModels.GraphModel;
import com.jaamsim.DisplayModels.IconModel;
import com.jaamsim.DisplayModels.ImageModel;
import com.jaamsim.DisplayModels.PolylineModel;
import com.jaamsim.DisplayModels.ShapeModel;
import com.jaamsim.DisplayModels.TextModel;
import com.jaamsim.basicsim.Entity;
import com.jaamsim.basicsim.ErrorException;
import com.jaamsim.basicsim.ObjectType;
import com.jaamsim.basicsim.Simulation;
import com.jaamsim.datatypes.DoubleVector;
import com.jaamsim.input.BooleanInput;
import com.jaamsim.input.EntityInput;
import com.jaamsim.input.EntityListInput;
import com.jaamsim.input.EnumInput;
import com.jaamsim.input.Input;
import com.jaamsim.input.InputAgent;
import com.jaamsim.input.InputErrorException;
import com.jaamsim.input.Keyword;
import com.jaamsim.input.KeywordIndex;
import com.jaamsim.input.Output;
import com.jaamsim.input.RelativeEntityInput;
import com.jaamsim.input.ValueListInput;
import com.jaamsim.input.Vec3dInput;
import com.jaamsim.input.Vec3dListInput;
import com.jaamsim.math.Color4d;
import com.jaamsim.math.Mat4d;
import com.jaamsim.math.Quaternion;
import com.jaamsim.math.Transform;
import com.jaamsim.math.Vec3d;
import com.jaamsim.render.DisplayModelBinding;
import com.jaamsim.render.RenderUtils;
import com.jaamsim.render.VisibilityInfo;
import com.jaamsim.ui.FrameBox;
import com.jaamsim.ui.GUIFrame;
import com.jaamsim.ui.View;
import com.jaamsim.units.AngleUnit;
import com.jaamsim.units.DimensionlessUnit;
import com.jaamsim.units.DistanceUnit;
import com.jogamp.newt.event.KeyEvent;

/**
 * Encapsulates the methods and data needed to display a simulation object in the 3D environment.
 * Extends the basic functionality of entity in order to have access to the basic system
 * components like the eventManager.
 */
public class DisplayEntity extends Entity {

	@Keyword(description = "The location of the object in {x, y, z} coordinates.",
	         exampleList = {"-3.922 -1.830 0.000 m"})
	protected final Vec3dInput positionInput;

	@Keyword(description = "The point within the object that is located at the coordinates of "
	                     + "its Position input. Expressed with respect to a unit box centered "
	                     + "about { 0 0 0 }.",
	         exampleList = {"-0.5 -0.5 0.0"})
	protected final Vec3dInput alignmentInput;

	@Keyword(description = "The size of the object in {x, y, z} coordinates.",
	         exampleList = {"15 12 0 m"})
	protected final Vec3dInput sizeInput;

	@Keyword(description = "Euler angles defining the rotation of the object.",
	         exampleList = {"0 0 90 deg"})
	private final Vec3dInput orientationInput;

	@Keyword(description = "A list of points in {x, y, z} coordinates that defines a polyline.",
	         exampleList = {"{ 1.0 1.0 0.0 m } { 2.0 2.0 0.0 m } { 3.0 3.0 0.0 m }",
	                       "{ 1.0 1.0 m } { 2.0 2.0 m } { 3.0 3.0 m }"})
	protected final Vec3dListInput pointsInput;

	@Keyword(description = "The type of curve interpolation used for line type entities.",
	         exampleList = {"LINEAR", "BEZIER", "SPLINE"})
	protected final EnumInput<PolylineInfo.CurveType> curveTypeInput;

	@Keyword(description = "If a Region is specified, the Position and Orientation inputs for "
	                     + "the present object will be relative to the Position and Orientation "
	                     + "of the specified Region. If the specified Region is moved or "
	                     + "rotated, the present object with move to maintain it relative "
	                     + "position and orientation.",
	         exampleList = {"Region1"})
	protected final EntityInput<Region> regionInput;

	@Keyword(description = "If an object is specified, the Position input for the present object "
	                     + "will be relative to the Position for the specified object. If the "
	                     + "specified object is moved, the present object will move to maintain "
	                     + "its relative position.",
	         exampleList = {"DisplayEntity1"})
	protected final RelativeEntityInput relativeEntity;

	@Keyword(description = "The graphic representation of the object. If a list of DisplayModels "
	                     + "is entered, each one will be displayed provided that its DrawRange "
	                     + "input is satisfied. This feature allows the object's appearance to "
	                     + "change with its distance from the View window's camera.",
	         exampleList = {"ColladaModel1", "ColladaModel1 ColladaModel2"})
	protected final EntityListInput<DisplayModel> displayModelListInput;

	@Keyword(description = "If TRUE, the object is displayed in the View windows.",
	         exampleList = {"FALSE"})
	private final BooleanInput showInput;

	@Keyword(description = "If TRUE, the object is active and used in simulation runs.",
	         exampleList = {"FALSE"})
	private final BooleanInput active;

	@Keyword(description = "If TRUE, the object will respond to mouse clicks and can be "
	                     + "positioned by dragging with the mouse.",
	         exampleList = {"FALSE"})
	private final BooleanInput movable;

	@Keyword(description = "The view windows on which this entity will be visible.",
	         exampleList = {"View2 View3"})
	private final EntityListInput<View> visibleViews;

	@Keyword(description = "The distances from the camera that this entity will be visible",
	         exampleList = {"0 100 m"})
	private final ValueListInput drawRange;

	private final Vec3d position = new Vec3d();
	private final Vec3d size = new Vec3d(1.0d, 1.0d, 1.0d);
	private final Vec3d orient = new Vec3d();
	private final Vec3d align = new Vec3d();
	private final ArrayList<DisplayModel> displayModelList = new ArrayList<>();
	private boolean show;

	private Region currentRegion;

	private ArrayList<DisplayModelBinding> modelBindings;
	private VisibilityInfo visInfo = null;

	private final HashMap<String, Tag> tagMap = new HashMap<>();

	private static final ArrayList<Vec3d> defPoints =  new ArrayList<>();
	private static final DoubleVector defRange = new DoubleVector(2);
	static {
		defPoints.add(new Vec3d(0.0d, 0.0d, 0.0d));
		defPoints.add(new Vec3d(1.0d, 0.0d, 0.0d));

		defRange.add(0.0d);
		defRange.add(Double.POSITIVE_INFINITY);
	}

	{
		positionInput = new Vec3dInput("Position", GRAPHICS, new Vec3d());
		positionInput.setUnitType(DistanceUnit.class);
		this.addInput(positionInput);

		alignmentInput = new Vec3dInput("Alignment", GRAPHICS, new Vec3d());
		this.addInput(alignmentInput);

		sizeInput = new Vec3dInput("Size", GRAPHICS, new Vec3d(1.0d, 1.0d, 1.0d));
		sizeInput.setUnitType(DistanceUnit.class);
		sizeInput.setValidRange(0.0d, Double.POSITIVE_INFINITY);
		this.addInput(sizeInput);

		orientationInput = new Vec3dInput("Orientation", GRAPHICS, new Vec3d());
		orientationInput.setUnitType(AngleUnit.class);
		this.addInput(orientationInput);

		pointsInput = new Vec3dListInput("Points", GRAPHICS, defPoints);
		pointsInput.setValidCountRange( 2, Integer.MAX_VALUE );
		pointsInput.setUnitType(DistanceUnit.class);
		this.addInput(pointsInput);

		curveTypeInput = new EnumInput<>(PolylineInfo.CurveType.class, "CurveType", GRAPHICS, PolylineInfo.CurveType.LINEAR);
		this.addInput(curveTypeInput);

		regionInput = new EntityInput<>(Region.class, "Region", GRAPHICS, null);
		this.addInput(regionInput);

		relativeEntity = new RelativeEntityInput("RelativeEntity", GRAPHICS, null);
		relativeEntity.setEntity(this);
		this.addInput(relativeEntity);

		displayModelListInput = new EntityListInput<>( DisplayModel.class, "DisplayModel", GRAPHICS, null);
		displayModelListInput.addInvalidClass(IconModel.class);
		displayModelListInput.addInvalidClass(TextModel.class);
		displayModelListInput.addInvalidClass(GraphModel.class);
		this.addInput(displayModelListInput);
		displayModelListInput.setUnique(false);

		active = new BooleanInput("Active", KEY_INPUTS, true);
		active.setHidden(true);
		this.addInput(active);

		showInput = new BooleanInput("Show", GRAPHICS, true);
		this.addInput(showInput);

		movable = new BooleanInput("Movable", GRAPHICS, true);
		this.addInput(movable);

		visibleViews = new EntityListInput<>(View.class, "VisibleViews", GRAPHICS, null);
		visibleViews.setDefaultText("All Views");
		this.addInput(visibleViews);

		drawRange = new ValueListInput("DrawRange", GRAPHICS, defRange);
		drawRange.setUnitType(DistanceUnit.class);
		drawRange.setValidCount(2);
		drawRange.setValidRange(0, Double.POSITIVE_INFINITY);
		this.addInput(drawRange);
	}

	/**
	 * Constructor: initializing the DisplayEntity's graphics
	 */
	public DisplayEntity() {

		ObjectType type = this.getObjectType();
		if (type == null)
			return;

		// Set the default DisplayModel
		displayModelListInput.setDefaultValue(type.getDefaultDisplayModel());
		this.setDisplayModelList(type.getDefaultDisplayModel());

		// Set the default size
		sizeInput.setDefaultValue(type.getDefaultSize());
		this.setSize(type.getDefaultSize());

		// Set the default Alignment
		alignmentInput.setDefaultValue(type.getDefaultAlignment());
		this.setAlignment(type.getDefaultAlignment());

		this.setShow(showInput.getValue());

		// Choose which set of keywords to show
		this.setGraphicsKeywords();
	}

	@Override
	public void setInputsForDragAndDrop() {

		// Determine whether the entity should sit on top of the x-y plane
		boolean alignBottom = true;
		ArrayList<DisplayModel> displayModels = displayModelListInput.getValue();
		if (displayModels != null && displayModels.size() > 0) {
			DisplayModel dm0 = displayModels.get(0);
			if (dm0 instanceof ShapeModel || dm0 instanceof ImageModel || dm0 instanceof TextModel )
				alignBottom = false;
		}

		if (this.usePointsInput() || alignmentInput.getHidden() || getSize().z == 0.0d) {
			alignBottom = false;
		}

		if (alignBottom)
			InputAgent.applyArgs(this, "Alignment", "0.0", "0.0", "-0.5");
	}

	@Override
	public void updateForInput( Input<?> in ) {
		super.updateForInput( in );

		if (in == positionInput) {
			this.setPosition(positionInput.getValue());
			return;
		}
		if (in == sizeInput) {
			this.setSize(sizeInput.getValue());
			return;
		}
		if (in == orientationInput) {
			this.setOrientation(orientationInput.getValue());
			return;
		}
		if (in == alignmentInput) {
			this.setAlignment(alignmentInput.getValue());
			return;
		}
		if (in == regionInput) {
			this.setRegion(regionInput.getValue());
			return;
		}
		if (in == displayModelListInput) {
			this.setDisplayModelList(displayModelListInput.getValue());
			return;
		}
		if (in == showInput) {
			this.setShow(showInput.getValue());
			return;
		}

		// If Points were input, then use them to set the start and end coordinates
		if (in == pointsInput || in == curveTypeInput) {
			invalidateScreenPoints();
			return;
		}

		if (in == visibleViews || in == drawRange) {
			if (visibleViews.isDefault() && drawRange.isDefault()) {
				visInfo = null;
			}
			double minDist = drawRange.getValue().get(0);
			double maxDist = drawRange.getValue().get(1);
			// It's possible for the distance to be behind the camera, yet have the object visible (distance is to center)
			// So instead use negative infinity in place of zero to never cull when close to the camera.
			if (minDist == 0.0) {
				minDist = Double.NEGATIVE_INFINITY;
			}
			visInfo = new VisibilityInfo(visibleViews.getValue(), minDist, maxDist);
			return;
		}
	}

	@Override
	public void validate()
	throws InputErrorException {
		super.validate();

		if (getDisplayModelList() != null) {
			for (DisplayModel dm : getDisplayModelList()) {
				if (!dm.canDisplayEntity(this)) {
					error("Invalid DisplayModel: %s for this DisplayEntity", dm.getName());
				}
			}
		}
	}

	@Override
	public void earlyInit() {
		super.earlyInit();
		if (! this.testFlag(FLAG_GENERATED)) {
			this.resetGraphics();
		}
	}

	@Override
	public void delete() {

		// Kill the label
		if (! this.testFlag(FLAG_GENERATED)) {
			EntityLabel label = EntityLabel.getLabel(this);
			if (label != null)
				label.delete();
		}

		// Kill the DisplayEntity
		super.delete();

		// Clear the properties
		currentRegion = null;
	}

	/**
	 * Restores the initial appearance of this entity.
	 */
	public void resetGraphics() {
		this.setPosition(positionInput.getValue());
		this.setSize(sizeInput.getValue());
		this.setAlignment(alignmentInput.getValue());
		this.setOrientation(orientationInput.getValue());
		this.setDisplayModelList(displayModelListInput.getValue());
		this.setRegion(regionInput.getValue());
		this.setShow(showInput.getValue());
	}

	private void showStandardGraphicsKeywords(boolean bool) {
		positionInput.setHidden(!bool);
		sizeInput.setHidden(!bool);
		alignmentInput.setHidden(!bool);
		orientationInput.setHidden(!bool);
	}

	private void showPolylineGraphicsKeywords(boolean bool) {
		pointsInput.setHidden(!bool);
		curveTypeInput.setHidden(!bool);
	}

	public boolean usePointsInput() {
		ArrayList<DisplayModel> dmList = displayModelListInput.getValue();
		if (dmList == null || dmList.isEmpty())
			return false;
		return dmList.get(0) instanceof PolylineModel;
	}

	private void setGraphicsKeywords() {

		// No displaymodel
		if (this instanceof OverlayEntity || displayModelListInput.getValue() == null) {
			showStandardGraphicsKeywords(false);
			showPolylineGraphicsKeywords(false);
			regionInput.setHidden(true);
			relativeEntity.setHidden(true);
			showInput.setHidden(true);
			movable.setHidden(true);
			return;
		}

		// Polyline type displaymodel
		if (usePointsInput()) {
			showStandardGraphicsKeywords(false);
			showPolylineGraphicsKeywords(true);
			return;
		}

		// Standard displaymodel
		showStandardGraphicsKeywords(true);
		showPolylineGraphicsKeywords(false);
	}

	public Vec3d getPosition() {
		synchronized (position) {
			return new Vec3d(position);
		}
	}

	public void setPosition(Vec3d pos) {
		synchronized (position) {
			position.set3(pos);
		}
	}

	public Vec3d getSize() {
		synchronized (position) {
			return new Vec3d(size);
		}
	}

	public void setSize(Vec3d size) {
		synchronized (position) {
			this.size.set3(size);
		}
	}

	public Vec3d getOrientation() {
		synchronized (position) {
			return new Vec3d(orient);
		}
	}

	public void setOrientation(Vec3d orientation) {
		synchronized (position) {
			orient.set3(orientation);
		}
	}

	public Vec3d getAlignment() {
		synchronized (position) {
			return new Vec3d(align);
		}
	}

	public void setAlignment(Vec3d align) {
		synchronized (position) {
			this.align.set3(align);
		}
	}

	public boolean getShow() {
		synchronized (position) {
			return show;
		}
	}

	public void setShow(boolean bool) {
		synchronized (position) {
			show = bool;
		}
	}

	public boolean isActive() {
		return active.getValue();
	}

	public boolean isMovable() {
		return movable.getValue();
	}

	public DisplayEntity getRelativeEntity() {
		return relativeEntity.getValue();
	}

	public ArrayList<String> getRelativeEntityOptions() {
		return relativeEntity.getValidOptions();
	}

	public ArrayList<String> getRegionOptions() {
		return regionInput.getValidOptions();
	}

	public Region getCurrentRegion() {
		return currentRegion;
	}

	/**
	 * Removes the entity from its current region and assigns a new region
	 * @param newRegion - the region the entity will be assigned to
	 */
	public void setRegion( Region newRegion ) {
		currentRegion = newRegion;
	}

	public ArrayList<View> getVisibleViews() {
		return visibleViews.getValue();
	}

	/**
	 * Update any internal stated needed by either renderer.
	 */
	public void updateGraphics(double simTime) {
	}

	private void calculateEulerRotation(Vec3d val, Vec3d euler) {
		double sinx = Math.sin(euler.x);
		double siny = Math.sin(euler.y);
		double sinz = Math.sin(euler.z);
		double cosx = Math.cos(euler.x);
		double cosy = Math.cos(euler.y);
		double cosz = Math.cos(euler.z);

		// Calculate a 3x3 rotation matrix
		double m00 = cosy * cosz;
		double m01 = -(cosx * sinz) + (sinx * siny * cosz);
		double m02 = (sinx * sinz) + (cosx * siny * cosz);

		double m10 = cosy * sinz;
		double m11 = (cosx * cosz) + (sinx * siny * sinz);
		double m12 = -(sinx * cosz) + (cosx * siny * sinz);

		double m20 = -siny;
		double m21 = sinx * cosy;
		double m22 = cosx * cosy;

		double x = m00 * val.x + m01 * val.y + m02 * val.z;
		double y = m10 * val.x + m11 * val.y + m12 * val.z;
		double z = m20 * val.x + m21 * val.y + m22 * val.z;

		val.set3(x, y, z);
	}

	public Vec3d getPositionForAlignment(Vec3d alignment) {
		Vec3d temp = new Vec3d(alignment);
		synchronized (position) {
			temp.sub3(align);
			temp.mul3(size);
			calculateEulerRotation(temp, orient);
			temp.add3(position);
		}

		return temp;
	}

	public Vec3d getGlobalPositionForAlignment(Vec3d alignment) {
		Vec3d temp = new Vec3d(alignment);
		synchronized (position) {
			temp.sub3(align);
			temp.mul3(size);
			calculateEulerRotation(temp, orient);
			temp.add3(this.getGlobalPosition());
		}

		return temp;
	}

	/**
	 * Returns the transformation that converts a point in the entity's
	 * coordinates to the global coordinate system.
	 * <p>
	 * The entity's coordinate system is centred on the entity's alignment point
	 * and its axes are rotated by the entity's orientation angles. It is NOT
	 * scaled by the entity's size, so the coordinates still have units of
	 * metres. The effects of the RelativeEntity and Region inputs are included
	 * in the transformation.
	 * @return global coordinates for the point.
	 */
	public Transform getGlobalTrans() {
		return getGlobalTransForSize(size);
	}

	/**
	 * Returns the equivalent global transform for this entity as if 'sizeIn' where the actual
	 * size.
	 * @param sizeIn
	 * @param simTime
	 * @return
	 */
	public Transform getGlobalTransForSize(Vec3d sizeIn) {
		// Okay, this math may be hard to follow, this is effectively merging two TRS transforms,
		// The first is a translation only transform from the alignment parameter
		// Then a transform is built up based on position and orientation
		// As size is a non-uniform scale it can not be represented by the jaamsim TRS Transform and therefore
		// not actually included in this result, except to adjust the alignment

		// Alignment transformations
		Vec3d temp = new Vec3d(sizeIn);
		temp.mul3(align);
		temp.scale3(-1.0d);
		Transform alignTrans = new Transform(temp);

		// Orientation transformation
		Quaternion rot = new Quaternion();
		rot.setEuler3(orient);
		Transform ret = new Transform(null, rot, 1);

		// Combine the alignment and orientation transformations
		ret.merge(ret, alignTrans);

		// Convert the alignment/orientation transformation to the global coordinate system
		if (currentRegion != null)
			ret.merge(currentRegion.getRegionTransForVectors(), ret);

		// Offset the transformation by the entity's global position vector
		ret.getTransRef().add3(getGlobalPosition());

		return ret;
	}

	/**
	 * Returns the transformation that converts a point in the global
	 * coordinate system to the entity's coordinates.
	 * <p>
	 * The entity's coordinate system is centred on the entity's alignment point
	 * and its axes are rotated by the entity's orientation angles. It is NOT
	 * scaled by the entity's size, so the coordinates still have units of
	 * metres. The effects of the RelativeEntity and Region inputs are included
	 * in the transformation.
	 * @return local coordinates for the point.
	 */
	public Transform getEntityTransForSize(Vec3d sizeIn) {
		Transform trans = new Transform();
		getGlobalTransForSize(sizeIn).inverse(trans);
		return trans;
	}

	/**
	 * Returns the global transform with scale factor all rolled into a Matrix4d
	 * @return
	 */
	public Mat4d getTransMatrix() {
		Transform trans = getGlobalTrans();
		Mat4d ret = new Mat4d();
		trans.getMat4d(ret);
		ret.scaleCols3(getSize());
		return ret;
	}

	/**
	 * Returns the inverse global transform with scale factor all rolled into a Matrix4d
	 * @return
	 */
	public Mat4d getInvTransMatrix() {
		return RenderUtils.getInverseWithScale(getGlobalTrans(), size);
	}

	/**
	 * Return the position in the global coordinate system
	 * @return
	 */
	public Vec3d getGlobalPosition() {
		return getGlobalPosition(getPosition());
	}

	/**
	 * Convert the specified local coordinate to the global coordinate system
	 * @param pos - a position in the entity's local coordinate system
	 * @return
	 */
	public Vec3d getGlobalPosition(Vec3d pos) {

		Vec3d ret = new Vec3d(pos);

		// Position is relative to another entity
		DisplayEntity ent = this.getRelativeEntity();
		if (ent != null) {
			if (currentRegion != null)
				currentRegion.getRegionTransForVectors().multAndTrans(ret, ret);
			ret.add3(ent.getGlobalPosition());
			return ret;
		}

		// Position is given in a local coordinate system
		if (currentRegion != null)
			currentRegion.getRegionTrans().multAndTrans(ret, ret);

		return ret;
	}

	/**
	 * Returns the global coordinates for a specified array of local coordinates.
	 * @return
	 */
	public ArrayList<Vec3d> getGlobalPosition(ArrayList<Vec3d> pts) {
		ArrayList<Vec3d> ret = new ArrayList<>(pts.size());
		for (Vec3d pt : pts) {
			ret.add(getGlobalPosition(pt));
		}
		return ret;
	}

	/**
	 * Returns the local coordinates for a specified array of global coordinates.
	 * @param pos - a position in the global coordinate system
	 */
	public ArrayList<Vec3d> getLocalPosition(ArrayList<Vec3d> pts) {
		ArrayList<Vec3d> ret = new ArrayList<>(pts.size());
		for (Vec3d pt : pts) {
			ret.add(getLocalPosition(pt));
		}
		return ret;
	}

	/*
	 * Returns the center relative to the origin
	 */
	public Vec3d getAbsoluteCenter() {
		Vec3d cent = this.getPositionForAlignment(new Vec3d());
		DisplayEntity ent = this.getRelativeEntity();
		if (ent != null)
			cent.add3(ent.getGlobalPosition());

		return cent;
	}

	public void setGlobalPosition(Vec3d pos) {
		setPosition(getLocalPosition(pos));
	}

	/**
	 * Returns the local coordinates for this entity corresponding to the
	 * specified global coordinates.
	 * @param pos - a position in the global coordinate system
	 */
	public Vec3d getLocalPosition(Vec3d pos) {

		Vec3d localPos = new Vec3d(pos);

		// Position is relative to another entity
		DisplayEntity ent = this.getRelativeEntity();
		if (ent != null) {
			localPos.sub3(ent.getGlobalPosition());
			if (currentRegion != null)
				currentRegion.getInverseRegionTransForVectors().multAndTrans(localPos, localPos);
			return localPos;
		}

		// Position is given in a local coordinate system
		if (currentRegion != null)
			currentRegion.getInverseRegionTrans().multAndTrans(pos, localPos);

		return localPos;
	}

	/*
	 * move object to argument point based on alignment
	 */
	public void setPositionForAlignment(Vec3d alignment, Vec3d position) {
		// Calculate the difference between the desired point and the current aligned position
		Vec3d diff = this.getPositionForAlignment(alignment);
		diff.sub3(position);
		diff.scale3(-1.0d);

		// add the difference to the current position and set the new position
		diff.add3(getPosition());
		setPosition(diff);
	}

	public void setGlobalPositionForAlignment(Vec3d alignment, Vec3d position) {
		// Calculate the difference between the desired point and the current aligned position
		Vec3d diff = this.getGlobalPositionForAlignment(alignment);
		diff.sub3(position);
		diff.scale3(-1.0d);

		// add the difference to the current position and set the new position
		diff.add3(getPosition());
		setPosition(diff);
	}

	/**
	 * Returns the transformation to global coordinates from the local
	 * coordinate system determined by the entity's Region and RelativeEntity
	 * inputs.
	 * <p>
	 * Note that this local coordinate system is centred on the position of
	 * the RelativeEntity, not on the position of this entity.
	 * @return transformation to global coordinates.
	 */
	public Transform getGlobalPositionTransform() {
		Transform ret =  new Transform(null, null, 1.0d);

		// Position is relative to another entity
		DisplayEntity relEnt = this.getRelativeEntity();
		if (relEnt != null) {
			if (currentRegion != null)
				ret = currentRegion.getRegionTransForVectors();
			ret.getTransRef().add3(relEnt.getGlobalPosition());
			return ret;
		}

		// Position is given in a local coordinate system
		if (currentRegion != null)
			ret = currentRegion.getRegionTrans();

		return ret;
	}

	public ArrayList<DisplayModel> getDisplayModelList() {
		return displayModelList;
	}

	public void setDisplayModelList(ArrayList<DisplayModel> dmList) {
		displayModelList.clear();
		if (dmList == null)
			return;
		for (DisplayModel dm : dmList) {
			displayModelList.add(dm);
		}
		clearBindings(); // Clear this on any change, and build it lazily later
	}

	public final void clearBindings() {
		modelBindings = null;
	}

	public ArrayList<DisplayModelBinding> getDisplayBindings() {
		if (modelBindings == null) {
			// Populate the model binding list
			if (getDisplayModelList() == null) {
				modelBindings = new ArrayList<>();
				return modelBindings;
			}
			modelBindings = new ArrayList<>(getDisplayModelList().size());
			for (int i = 0; i < getDisplayModelList().size(); ++i) {
				DisplayModel dm = getDisplayModelList().get(i);
				modelBindings.add(dm.getBinding(this));
			}
		}
		return modelBindings;
	}

	public VisibilityInfo getVisibilityInfo() {
		return visInfo;
	}

	public void dragged(Vec3d newPos) {

		KeywordIndex kw = InputAgent.formatVec3dInput(positionInput.getKeyword(), newPos, DistanceUnit.class);
		InputAgent.apply(this, kw);

		ArrayList<Vec3d> points = pointsInput.getValue();
		if (points == null || points.isEmpty())
			return;
		Vec3d dist = new Vec3d(newPos);
		dist.sub3(points.get(0));
		kw = InputAgent.formatPointsInputs(pointsInput.getKeyword(), pointsInput.getValue(), dist);
		InputAgent.apply(this, kw);
	}

	public void handleKeyPressed(int keyCode, char keyChar, boolean shift, boolean control, boolean alt) {
		if (!isMovable())
			return;
		Vec3d pos = getPosition();
		double inc = Simulation.getIncrementSize();
		if (Simulation.isSnapToGrid())
			inc = Math.max(inc, Simulation.getSnapGridSpacing());
		switch (keyCode) {

			case KeyEvent.VK_LEFT:
				pos.x -= inc;
				break;

			case KeyEvent.VK_RIGHT:
				pos.x +=inc;
				break;

			case KeyEvent.VK_UP:
				if (shift)
					pos.z += inc;
				else
					pos.y += inc;
				break;

			case KeyEvent.VK_DOWN:
				if (shift)
					pos.z -= inc;
				else
					pos.y -= inc;
				break;

			default:
				return;
		}
		if (Simulation.isSnapToGrid())
			pos = Simulation.getSnapGridPosition(pos, pos, shift);

		KeywordIndex kw = InputAgent.formatVec3dInput(positionInput.getKeyword(), pos, DistanceUnit.class);
		InputAgent.storeAndExecute(new KeywordCommand(this, kw));
	}

	public void handleKeyReleased(int keyCode, char keyChar, boolean shift, boolean control, boolean alt) {
		if (keyCode == KeyEvent.VK_DELETE) {
			try {
				delete();
				FrameBox.setSelectedEntity(null, false);
			}
			catch (ErrorException e) {
				GUIFrame.invokeErrorDialog("User Error", e.getMessage());
			}
			return;
		}
	}

	public void handleMouseClicked(short count, Vec3d globalCoord) {}

	public boolean handleDrag(Vec3d currentPt, Vec3d firstPt) {
		return false;
	}

	/**
	 * An overloadable method that is called when the 'create link' feature is enabled and selection changes
	 * @param ent
	 */
	public void linkTo(DisplayEntity nextEnt) {
		// Do nothing in default behavior
	}

	/**
	 * Set the inputs for the two entities affected by a 'split' operation.
	 * @param splitEnt - entity split from the original
	 */
	public void setInputsForSplit(DisplayEntity splitEnt) {
		// Do nothing in default behavior
	}

	private final Object screenPointLock = new Object();
	private PolylineInfo[] cachedPointInfo;
	private ArrayList<Vec3d> cachedCurvePoints;

	protected final void invalidateScreenPoints() {
		synchronized(screenPointLock) {
			cachedPointInfo = null;
			cachedCurvePoints = null;
		}
	}

	public final PolylineInfo[] getScreenPoints(double simTime) {
		synchronized(screenPointLock) {
			if (cachedPointInfo == null)
				cachedPointInfo = this.buildScreenPoints(simTime);
			return cachedPointInfo;
		}
	}

	public PolylineInfo[] buildScreenPoints(double simTime) {
		PolylineInfo[] ret = new PolylineInfo[1];
		ret[0] = new PolylineInfo(getCurvePoints(), null, -1);
		return ret;
	}

	public ArrayList<Vec3d> getPoints() {
		synchronized(screenPointLock) {
			if (!this.usePointsInput())
				return null;
			return new ArrayList<>(pointsInput.getValue());
		}
	}

	public ArrayList<Vec3d> getCurvePoints() {
		synchronized(screenPointLock) {
			if (cachedCurvePoints == null)
				cachedCurvePoints = this.buildCurvePoints();
			return cachedCurvePoints;
		}
	}

	private ArrayList<Vec3d> buildCurvePoints() {
		ArrayList<Vec3d> ret = null;
		switch (this.getCurveType()) {
		case LINEAR:
			ret = getPoints();
			break;
		case BEZIER:
			ret = PolylineInfo.getBezierPoints(getPoints());
			break;
		case SPLINE:
			ret = PolylineInfo.getSplinePoints(getPoints());
			break;
		case CIRCULAR_ARC:
			ret = PolylineInfo.getCircularArcPoints(getPoints());
			break;
		default:
			assert(false);
			error("Invalid CurveType");
		}
		return ret;
	}

	public boolean selectable() {
		return true;
	}

	protected PolylineInfo.CurveType getCurveType() {
		return curveTypeInput.getValue();
	}

	public final void setTagColour(String tagName, Color4d ca) {
		Color4d cas[] = new Color4d[1] ;
		cas[0] = ca;
		setTagColours(tagName, cas);
	}

	public final void setTagColours(String tagName, Color4d[] cas) {
		Tag t = tagMap.get(tagName);
		if (t == null) {
			t = new Tag(cas, null, true);
			tagMap.put(tagName, t);
			return;
		}

		if (t.colorsMatch(cas))
			return;
		else
			tagMap.put(tagName, new Tag(cas, t.sizes, t.visible));
	}

	public final void setTagSize(String tagName, double size) {
		double s[] = new double[1] ;
		s[0] = size;
		setTagSizes(tagName, s);
	}

	public final void setTagSizes(String tagName, double[] sizes) {
		Tag t = tagMap.get(tagName);
		if (t == null) {
			t = new Tag(null, sizes, true);
			tagMap.put(tagName, t);
			return;
		}

		if (t.sizesMatch(sizes))
			return;
		else
			tagMap.put(tagName, new Tag(t.colors, sizes, t.visible));
	}

	public final void setTagVisibility(String tagName, boolean isVisible) {
		Tag t = tagMap.get(tagName);
		if (t == null) {
			t = new Tag(null, null, isVisible);
			tagMap.put(tagName, t);
			return;
		}

		if (t.visMatch(isVisible))
			return;
		else
			tagMap.put(tagName, new Tag(t.colors, t.sizes, isVisible));
	}

	/**
	 * Get all tags for this entity
	 * @return
	 */
	public HashMap<String, Tag> getTagSet() {
		return tagMap;
	}

	////////////////////////////////////////////////////////////////////////
	// Outputs
	////////////////////////////////////////////////////////////////////////

	@Output(name = "Position",
	 description = "The present {x, y, z} coordinates of the DisplayEntity in its region.",
	    unitType = DistanceUnit.class,
	    sequence = 0)
	public Vec3d getPosOutput(double simTime) {
		return getPosition();
	}

	@Output(name = "Size",
	 description = "The present {x, y, z} components of the DisplayEntity's size.",
	    unitType = DistanceUnit.class,
	    sequence = 1)
	public Vec3d getSizeOutput(double simTime) {
		return getSize();
	}

	@Output(name = "Orientation",
	 description = "The present {x, y, z} euler angles of the DisplayEntity's rotation.",
	    unitType = AngleUnit.class,
	    sequence = 2)
	public Vec3d getOrientOutput(double simTime) {
		return getOrientation();
	}

	@Output(name = "Alignment",
	 description = "The present {x, y, z} coordinates of a point on the DisplayEntity that aligns "
	             + "direction with the position output. Each component should be in the range "
	             + "[-0.5, 0.5].",
	    unitType = DimensionlessUnit.class,
	    sequence = 3)
	public Vec3d getAlignOutput(double simTime) {
		return getAlignment();
	}

	@Output(name = "GraphicalLength",
	 description = "Polyline type objects: the length of the polyline determined by its "
	             + "Points and CurveType inputs.\n"
	             + "Non-polyline type objects: the largest of the Size inputs.",
	    unitType = DistanceUnit.class,
	    sequence = 4)
	public double getGraphicalLength(double simTime) {
		if (usePointsInput()) {
			return PolylineInfo.getLength(getCurvePoints());
		}
		Vec3d vec = getSize();
		return Math.max(Math.max(vec.x, vec.y), vec.z);
	}

}
