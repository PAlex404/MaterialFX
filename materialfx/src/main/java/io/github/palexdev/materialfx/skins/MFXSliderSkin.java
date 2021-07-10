/*
 * Copyright (C) 2021 Parisi Alessandro
 * This file is part of MaterialFX (https://github.com/palexdev/MaterialFX).
 *
 * MaterialFX is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MaterialFX is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MaterialFX.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.github.palexdev.materialfx.skins;

import io.github.palexdev.materialfx.beans.NumberRange;
import io.github.palexdev.materialfx.controls.MFXSlider;
import io.github.palexdev.materialfx.controls.enums.SliderEnum.SliderMode;
import io.github.palexdev.materialfx.controls.enums.SliderEnum.SliderPopupSide;
import io.github.palexdev.materialfx.controls.factories.MFXAnimationFactory;
import io.github.palexdev.materialfx.font.MFXFontIcon;
import io.github.palexdev.materialfx.utils.AnimationUtils;
import io.github.palexdev.materialfx.utils.NodeUtils;
import io.github.palexdev.materialfx.utils.NumberUtils;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.Axis.TickMark;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MFXSliderSkin extends SkinBase<MFXSlider> {
    private final Rectangle track;
    private final Rectangle bar;
    private final Group group;
    private final Group ticksGroup;
    private final NumberAxis ticksAxis;
    private Node thumb;
    private Region popup;

    private final LayoutData layoutData = new LayoutData();
    private final PopupManager popupManager = new PopupManager();

    private double preDragThumbPos;
    private Point2D dragStart;
    private EventHandler<MouseEvent> thumbDragHandler;
    private EventHandler<MouseEvent> thumbPressHandler;
    private EventHandler<MouseEvent> trackPressedHandler;

    private boolean mousePressed = false;
    private boolean trackPressed = false;
    private boolean keyPressed = false;
    private boolean keyWasPressed = false;
    private PauseTransition releaseTimer = new PauseTransition();

    private boolean isSnapping = false;
    private boolean wasSnapping = false;

    public MFXSliderSkin(MFXSlider slider) {
        super(slider);

        track = buildRectangle("track");
        track.heightProperty().bind(slider.heightProperty());
        track.widthProperty().bind(slider.widthProperty());
        track.setFill(Color.rgb(82, 0, 237, 0.3));
        track.setStroke(Color.GOLD);

        bar = buildRectangle("bar");
        bar.heightProperty().bind(slider.heightProperty());
        bar.setFill(Color.GREEN);
        bar.setMouseTransparent(true);

        thumb = slider.getThumbSupplier().get();
        popup = slider.getPopupSupplier().get();
        popup.setVisible(false);
        popup.setOpacity(0.0);

        ticksAxis = new NumberAxis(slider.getMin(), slider.getMax(), slider.getTickUnit());
        ticksAxis.setMinorTickCount(slider.getMinorTicksCount());
        ticksAxis.setManaged(false);
        ticksAxis.setMouseTransparent(true);
        ticksAxis.setTickMarkVisible(false);
        ticksAxis.setTickLabelsVisible(false);

        Rectangle clip = new Rectangle();
        clip.heightProperty().bind(slider.heightProperty());
        clip.widthProperty().bind(slider.widthProperty());
        clip.arcHeightProperty().bind(track.arcHeightProperty());
        clip.arcWidthProperty().bind(track.arcWidthProperty());

        ticksGroup = new Group(ticksAxis);
        ticksGroup.setClip(clip);
        ticksGroup.setManaged(false);
        ticksGroup.setMouseTransparent(true);

        group = new Group(track, ticksGroup, bar, thumb, popup);
        group.setManaged(false);
        group.getStylesheets().add(slider.getUserAgentStylesheet());
        getChildren().setAll(group);

        releaseTimer.setDuration(Duration.millis(800));
        releaseTimer.setOnFinished(event -> hidePopup());

        thumbPressHandler = event -> {
            dragStart = thumb.localToParent(event.getX(), event.getY());
            preDragThumbPos = (slider.getValue() - slider.getMin()) / (slider.getMax() - slider.getMin());
        };
        thumbDragHandler = this::handleDrag;
        trackPressedHandler = this::trackPressed;

        if (slider.getOrientation() == Orientation.VERTICAL) {
            slider.setRotate(-90);
        } else {
            slider.setRotate(0);
        }

        setBehavior();
    }

    /**
     * Calls {@link #sliderHandlers()}, {@link #sliderListeners()}, {@link #skinBehavior()}.
     */
    protected void setBehavior() {
        sliderHandlers();
        sliderListeners();
        skinBehavior();
    }

    private void sliderHandlers() {
        MFXSlider slider = getSkinnable();

        /* FOCUS */
        slider.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> slider.requestFocus());

        /* POPUP HANDLING */
        slider.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            mousePressed = true;
            Node intersectedNode = event.getPickResult().getIntersectedNode();
            if (intersectedNode == track || NodeUtils.inHierarchy(intersectedNode, thumb)) {
                showPopup();
            }
        });
        slider.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            mousePressed = false;
            releaseTimer.playFromStart();
        });

        /* KEYBOARD HANDLING */
        slider.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            double val = (event.isShiftDown() || event.isControlDown()) ? slider.getAlternativeUnitIncrement() : slider.getUnitIncrement();

            if (isIncreaseKey(event)) {
                keyPressed = true;
                keyWasPressed = true;
                slider.setValue(
                        NumberUtils.clamp(slider.getValue() + val, slider.getMin(), slider.getMax())
                );
            } else if (isDecreaseKey(event)) {
                keyPressed = true;
                keyWasPressed = true;
                slider.setValue(
                        NumberUtils.clamp(slider.getValue() - val, slider.getMin(), slider.getMax())
                );
            }
        });
    }

    private void sliderListeners() {
        MFXSlider slider = getSkinnable();

        /* VALUE AND BOUNDS HANDLING */
        slider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!isSnapping) {
                updateLayout();
            }
        });
        slider.minProperty().addListener((observable, oldValue, newValue) -> {
            slider.setValue(0);
            ticksAxis.setLowerBound(newValue.doubleValue());
            slider.requestLayout();
        });
        slider.maxProperty().addListener((observable, oldValue, newValue) -> {
            slider.setValue(0);
            ticksAxis.setUpperBound(newValue.doubleValue());
            slider.requestLayout();
        });

        /* NumberAxis HANDLING */
        slider.minorTicksCountProperty().addListener((observable, oldValue, newValue) -> {
            ticksAxis.setMinorTickCount(newValue.intValue());
            ticksAxis.requestAxisLayout();
            slider.requestLayout();
        });
        slider.tickUnitProperty().addListener((observable, oldValue, newValue) -> {
            ticksAxis.setTickUnit(newValue.doubleValue());
            ticksAxis.requestAxisLayout();
            slider.requestLayout();
        });
        slider.showTicksAtEdgesProperty().addListener((observable, oldValue, newValue) -> slider.requestLayout());

        /* SUPPLIERS HANDLING */
        slider.popupSupplierProperty().addListener((observable, oldValue, newValue) -> {
            handlePopupChange();
            slider.requestLayout();
            popupManager.initPopup();
        });
        slider.thumbSupplierProperty().addListener((observable, oldValue, newValue) -> {
            handleThumbChange();
            slider.requestLayout();
        });

        /* FOCUS WORKAROUND HANDLING */
        slider.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue && keyPressed) {
                AnimationUtils.PauseBuilder.build()
                        .setDuration(Duration.millis(100))
                        .runWhile(slider.isFocused(), slider::requestFocus, () -> keyPressed = false);
            }
        });

        /* LAYOUT HANDLING */
        slider.bidirectionalProperty().addListener((observable, oldValue, newValue) -> slider.requestLayout());
        slider.orientationProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Orientation.VERTICAL) {
                slider.setRotate(-90);
            } else {
                slider.setRotate(0);
            }
        });
    }

    private void skinBehavior() {
        MFXSlider slider = getSkinnable();

        /* THUMB AND TRACK HANDLING */
        thumb.addEventHandler(MouseEvent.MOUSE_PRESSED, thumbPressHandler);
        thumb.addEventHandler(MouseEvent.MOUSE_DRAGGED, thumbDragHandler);
        track.addEventHandler(MouseEvent.MOUSE_PRESSED, trackPressedHandler);

        /* POPUP HANDLING */
        popupManager.initPopup();

        /* NumberAxis LAYOUT HANDLING */
        ticksAxis.visibleProperty().bind(slider.showMinorTicksProperty());
        ticksAxis.needsLayoutProperty().addListener((observable, oldValue, newValue) -> layoutData.updateTicksData());
    }

    private void updateLayout() {
        MFXSlider slider = getSkinnable();

        if (slider.getSliderMode() == SliderMode.SNAP_TO_TICKS && !keyWasPressed) {
            isSnapping = true;
            wasSnapping = true;
            double closest = layoutData.findNearestTick();
            slider.setValue(closest);
            isSnapping = false;
        }

        layoutData.update(false);

        if (!mousePressed) {
            showPopup();
            releaseTimer.playFromStart();
        }

        if ((trackPressed || wasSnapping) && slider.isAnimateOnPress()) {
            keyWasPressed = false;
            wasSnapping = false;
            AnimationUtils.ParallelBuilder.build()
                    .add(
                            new KeyFrame(Duration.millis(200), new KeyValue(bar.layoutXProperty(), layoutData.barX, MFXAnimationFactory.getInterpolatorV1()))
                    )
                    .add(
                            new KeyFrame(Duration.millis(200), new KeyValue(thumb.layoutXProperty(), layoutData.thumbX, MFXAnimationFactory.getInterpolatorV1())),
                            new KeyFrame(Duration.millis(200), new KeyValue(bar.widthProperty(), Math.abs(layoutData.barW), MFXAnimationFactory.getInterpolatorV1()))
                    )
                    .getAnimation()
                    .play();
        } else {
            thumb.setLayoutX(layoutData.thumbX);
            bar.setLayoutX(layoutData.barX);
            bar.setWidth(Math.abs(layoutData.barW));
        }
    }

    private void handleDrag(MouseEvent event) {
        MFXSlider slider = getSkinnable();
        trackPressed = false;

        Point2D curr = thumb.localToParent(event.getX(), event.getY());
        double dragPos = curr.getX() - dragStart.getX();
        double pos = preDragThumbPos + dragPos / slider.getWidth();
        double val = NumberUtils.clamp((pos * (slider.getMax() - slider.getMin())) + slider.getMin(), slider.getMin(), slider.getMax());
        slider.setValue(val);
    }

    private void trackPressed(MouseEvent event) {
        MFXSlider slider = getSkinnable();
        trackPressed = true;

        double pos = event.getX() / slider.getWidth();
        double val = NumberUtils.clamp((pos * (slider.getMax() - slider.getMin())) + slider.getMin(), slider.getMin(), slider.getMax());
        slider.setValue(val);
    }

    private void handlePopupChange() {
        MFXSlider slider = getSkinnable();

        int index = -1;
        if (popup != null) {
            index = group.getChildren().indexOf(popup);
            popup.layoutXProperty().unbind();
            popup.layoutYProperty().unbind();
            group.getChildren().remove(popup);
        }

        Supplier<Region> popupSupplier = slider.getPopupSupplier();
        popup = popupSupplier != null ? popupSupplier.get() : null;

        if (popup != null) {
            popup.setVisible(false);
            popup.setOpacity(0.0);
            group.getChildren().add(index >= 0 ? index : group.getChildren().size() - 1, popup);
            popupManager.initPopup();
        }
    }

    private void handleThumbChange() {
        MFXSlider slider = getSkinnable();

        int index = -1;
        if (thumb != null) {
            index = group.getChildren().indexOf(thumb);
            thumb.removeEventHandler(MouseEvent.MOUSE_PRESSED, thumbPressHandler);
            thumb.removeEventHandler(MouseEvent.MOUSE_DRAGGED, thumbDragHandler);
            group.getChildren().remove(thumb);
        }

        Supplier<Node> thumbSupplier = slider.getThumbSupplier();
        thumb = thumbSupplier != null ? thumbSupplier.get() : null;

        if (thumb != null) {
            thumb.addEventHandler(MouseEvent.MOUSE_PRESSED, thumbPressHandler);
            thumb.addEventHandler(MouseEvent.MOUSE_DRAGGED, thumbDragHandler);
            group.getChildren().add(index >= 0 ? index : group.getChildren().size() - 1, thumb);
        }
    }

    protected void showPopup() {
        if (popup == null) {
            return;
        }

        releaseTimer.stop();
        AnimationUtils.SequentialBuilder.build()
                .add(AnimationUtils.PauseBuilder.build().setDuration(Duration.ONE).setOnFinished(event -> popup.setVisible(true)).getAnimation())
                .add(new KeyFrame(Duration.millis(200), new KeyValue(popup.opacityProperty(), 1.0, Interpolator.EASE_IN)))
                .getAnimation()
                .play();
    }

    protected void hidePopup() {
        if (popup == null) {
            return;
        }

        AnimationUtils.SequentialBuilder.build()
                .add(new KeyFrame(Duration.millis(200), new KeyValue(popup.opacityProperty(), 0.0, Interpolator.EASE_OUT)))
                .setOnFinished(event -> popup.setVisible(false))
                .getAnimation()
                .play();
    }

    /**
     * Responsible for building the track and the bars for the progress bar.
     */
    protected Rectangle buildRectangle(String styleClass) {
        Rectangle rectangle = new Rectangle();
        rectangle.getStyleClass().setAll(styleClass);
        rectangle.setStroke(Color.TRANSPARENT);
        rectangle.setStrokeLineCap(StrokeLineCap.ROUND);
        rectangle.setStrokeLineJoin(StrokeLineJoin.ROUND);
        rectangle.setStrokeType(StrokeType.INSIDE);
        rectangle.setStrokeWidth(0);
        return rectangle;
    }

    protected Node buildTick() {
        return new MFXFontIcon("mfx-circle", 4);
    }

    private boolean isIncreaseKey(KeyEvent event) {
        MFXSlider slider = getSkinnable();

        return (event.getCode() == KeyCode.UP && slider.getOrientation() == Orientation.VERTICAL) ||
                (event.getCode() == KeyCode.RIGHT && slider.getOrientation() == Orientation.HORIZONTAL);
    }

    private boolean isDecreaseKey(KeyEvent event) {
        MFXSlider slider = getSkinnable();

        return (event.getCode() == KeyCode.DOWN && slider.getOrientation() == Orientation.VERTICAL) ||
                (event.getCode() == KeyCode.LEFT && slider.getOrientation() == Orientation.HORIZONTAL);
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return Math.max(100, leftInset + bar.prefWidth(getSkinnable().getWidth()) + rightInset);
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return Math.max(6, bar.prefHeight(width)) + topInset + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
    }

    @Override
    public void dispose() {
        super.dispose();

        thumb.removeEventHandler(MouseEvent.MOUSE_PRESSED, thumbPressHandler);
        thumb.removeEventHandler(MouseEvent.MOUSE_DRAGGED, thumbDragHandler);
        thumbPressHandler = null;
        thumbDragHandler = null;

        track.removeEventHandler(MouseEvent.MOUSE_PRESSED, trackPressedHandler);
        trackPressedHandler = null;

        releaseTimer = null;
    }

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);

        layoutData.update(true);
        thumb.relocate(layoutData.thumbX, layoutData.thumbY);
        bar.relocate(layoutData.barX, 0);
        bar.setWidth(Math.abs(layoutData.barW));

        ticksAxis.resize(w, h);
    }

    protected class LayoutData {
        private double zeroPos;
        private double thumbX;
        private double thumbY;
        private double barW;
        private double barX;

        private final ObservableList<TickData> ticksData = FXCollections.observableArrayList();
        private double ticksY;

        public void update(boolean isFullUpdate) {
            MFXSlider slider = getSkinnable();

            if (isFullUpdate) {
                double val;
                if (slider.getMin() > 0) {
                    val = slider.getMin();
                } else if (slider.getMax() < 0) {
                    val = slider.getMax();
                } else {
                    val = 0;
                }

                if (!slider.isBidirectional()) {
                    val = slider.getMin();
                }

                zeroPos = NumberUtils.mapOneRangeToAnother(
                        val,
                        NumberRange.of(slider.getMin(), slider.getMax()),
                        NumberRange.of(0.0, slider.getWidth()),
                        2
                );
                zeroPos = NumberUtils.clamp(zeroPos, 0, slider.getWidth());
            }

            thumbX = snapPositionX(
                    NumberUtils.mapOneRangeToAnother(
                            slider.getValue(),
                            NumberRange.of(slider.getMin(), slider.getMax()),
                            NumberRange.of(0.0, slider.getWidth()),
                            2
                    ) - halfThumbWidth());
            thumbY = snapPositionY(-halfThumbHeight() + (slider.getHeight() / 2));

            if (!slider.isBidirectional()) {
                barW = thumbX - zeroPos + (halfThumbWidth() * 3);
                barX = zeroPos - halfThumbWidth();
            } else {
                barW = slider.getValue() < 0 ? thumbX - zeroPos - halfThumbWidth() : thumbX - zeroPos + (halfThumbWidth() * 3);
                barX = slider.getValue() < 0 ? zeroPos + barW + halfThumbWidth() : zeroPos - halfThumbWidth();
            }
        }

        public void updateTicksData() {
            MFXSlider slider = getSkinnable();

            List<Double> ticksX = ticksAxis.getTickMarks().stream()
                    .map(TickMark::getPosition)
                    .collect(Collectors.toList());

            if (!ticksX.stream().allMatch(d -> d == 0)) {
                ticksGroup.getChildren().removeAll(getTicks());
                ticksData.clear();

                ObservableList<TickMark<Number>> tickMarks = ticksAxis.getTickMarks();
                for (int i = 0; i < tickMarks.size(); i++) {
                    TickMark<Number> tickMark = ticksAxis.getTickMarks().get(i);
                    TickData tickData = new TickData();
                    tickData.tick = buildTick();
                    tickData.tick.getStyleClass().setAll(NumberUtils.isEven(i) ? "tick-even" : "tick-odd");
                    tickData.tickVal = (double) tickMark.getValue();
                    tickData.x = snapPositionX(ticksX.get(i) - (tickData.halfTickWidth() / 1.5));
                    ticksData.add(tickData);

                    if (i == tickMarks.size() - 1) {
                        tickData.x -= tickData.halfTickWidth();
                    }
                }
                ticksY = snapPositionY(-ticksData.get(0).halfTickHeight() + (slider.getHeight() / 2));
                positionTicks();
            }
        }

        public void positionTicks() {
            MFXSlider slider = getSkinnable();
            if (!slider.isShowMajorTicks()) {
                return;
            }

            for (int i = 0; i < ticksData.size(); i++) {
                TickMark<Number> tickMark = ticksAxis.getTickMarks().get(i);
                TickData tickData = ticksData.get(i);

                if (!slider.isShowTicksAtEdges() &&
                        ((double) tickMark.getValue() == slider.getMax() || (double) tickMark.getValue() == slider.getMin())
                ) {
                    continue;
                }

                ticksGroup.getChildren().add(tickData.tick);
                tickData.tick.relocate(tickData.x, ticksY);
            }
        }

        public double findNearestTick() {
            MFXSlider slider = getSkinnable();

            double currVal = slider.getValue();
            return NumberUtils.closestValueTo(currVal, ticksData.stream().map(TickData::getTickVal).collect(Collectors.toList()));
        }

        public List<Node> getTicks() {
            return ticksData.stream().map(TickData::getTick).collect(Collectors.toList());
        }

        public double halfThumbWidth() {
            return thumb.prefWidth(-1) / 2;
        }

        public double halfThumbHeight() {
            return thumb.prefHeight(-1) / 2;
        }
    }

    protected class PopupManager {
        private DoubleBinding xBinding;
        private DoubleBinding yBinding;
        private DoubleBinding rotate;

        private void initPopup() {
            MFXSlider slider = getSkinnable();
            if (popup == null) {
                return;
            }

            xBinding = Bindings.createDoubleBinding(
                    this::computeXPos,
                    thumb.layoutXProperty(), popup.widthProperty(), slider.thumbSupplierProperty(), slider.orientationProperty(), slider.popupSideProperty()
            );
            yBinding = Bindings.createDoubleBinding(
                    this::computeYPos,
                    thumb.layoutYProperty(), popup.heightProperty(), slider.thumbSupplierProperty(), slider.orientationProperty(), slider.popupSideProperty()
            );
            rotate = Bindings.createDoubleBinding(
                    this::computeRotate,
                    slider.orientationProperty(), slider.popupSideProperty()
            );

            popup.rotateProperty().bind(rotate);
            popup.layoutXProperty().bind(xBinding);
            popup.layoutYProperty().bind(yBinding);
        }

        private double computeRotate() {
            MFXSlider slider = getSkinnable();

            if (slider.getOrientation() == Orientation.HORIZONTAL && slider.getPopupSide() == SliderPopupSide.OTHER_SIDE) {
                return 180;
            }

            if (slider.getOrientation() == Orientation.VERTICAL) {
                return slider.getPopupSide() == SliderPopupSide.DEFAULT ? 90 : -90;
            }

            return 0;
        }

        private double computeXPos() {
            MFXSlider slider = getSkinnable();

            double x;
            if (slider.getOrientation() == Orientation.HORIZONTAL) {
                x = thumb.getLayoutX() - ((popup.getWidth() - layoutData.halfThumbWidth() * 2) / 2);
                x = slider.getPopupSide() == SliderPopupSide.DEFAULT ? x : x - 1;
            } else {
                x = thumb.getLayoutX() - (popup.getHeight() / 2) + (layoutData.halfThumbWidth() / 2) + 1;
            }

            return snapPositionX(x);
        }

        private double computeYPos() {
            MFXSlider slider = getSkinnable();

            double y;
            if (slider.getOrientation() == Orientation.HORIZONTAL) {
                if (slider.getPopupSide() == SliderPopupSide.DEFAULT) {
                    y = -(popup.getHeight() + layoutData.halfThumbHeight() + slider.getPopupPadding());
                } else {
                    y = slider.getHeight() + layoutData.halfThumbHeight() + slider.getPopupPadding();
                }
            } else {
                if (slider.getPopupSide() == SliderPopupSide.DEFAULT) {
                    y = -(popup.getWidth() + layoutData.halfThumbHeight() + (slider.getPopupPadding() / 1.5));
                } else {
                    y = (slider.getHeight() * 1.5) + layoutData.halfThumbHeight() + slider.getPopupPadding();
                }
            }

            return snapPositionY(y);
        }
    }

    protected static class TickData {
        private Node tick;
        private double tickVal;
        private double x;

        public Node getTick() {
            return tick;
        }

        public double getTickVal() {
            return tickVal;
        }

        public double getX() {
            return x;
        }

        public double halfTickHeight() {
            return tick == null ? 0 : tick.prefHeight(-1) / 2;
        }

        public double halfTickWidth() {
            return tick == null ? 0 : tick.prefWidth(-1) / 2;
        }
    }
}