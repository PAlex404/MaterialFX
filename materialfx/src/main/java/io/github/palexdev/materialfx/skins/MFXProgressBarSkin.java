package io.github.palexdev.materialfx.skins;

import io.github.palexdev.materialfx.controls.MFXProgressBar;
import javafx.animation.*;
import javafx.scene.Group;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;
import javafx.util.Duration;

/**
 * This is the implementation of the {@code Skin} associated with every {@link MFXProgressBar}.
 */
public class MFXProgressBarSkin extends SkinBase<MFXProgressBar> {
    //================================================================================
    // Properties
    //================================================================================
    private final StackPane container;
    private final Rectangle track;
    private final Rectangle bar1;
    private final Rectangle bar2;

    private ParallelTransition indeterminateAnimation;

    //================================================================================
    // Constructors
    //================================================================================
    public MFXProgressBarSkin(MFXProgressBar progressBar) {
        super(progressBar);

        track = buildRectangle("track");
        track.heightProperty().bind(progressBar.heightProperty());
        track.widthProperty().bind(progressBar.widthProperty());

        bar1 = buildRectangle("bar1");
        bar1.heightProperty().bind(progressBar.heightProperty());

        bar2 = buildRectangle("bar2");
        bar2.heightProperty().bind(progressBar.heightProperty());
        bar2.visibleProperty().bind(progressBar.indeterminateProperty());

        Rectangle clip = new Rectangle();
        clip.heightProperty().bind(progressBar.heightProperty());
        clip.widthProperty().bind(progressBar.widthProperty());
        clip.arcHeightProperty().bind(track.arcHeightProperty());
        clip.arcWidthProperty().bind(track.arcWidthProperty());

        Group group = new Group(track, bar1, bar2);
        group.setClip(clip);
        group.setManaged(false);

        container = new StackPane(group);
        getChildren().setAll(container);

        setListeners();
    }

    //================================================================================
    // Methods
    //================================================================================

    /**
     * Adds listeners for: progress, width, visible, parent,scene and animation speed properties.
     */
    private void setListeners() {
        MFXProgressBar progressBar = getSkinnable();

        progressBar.progressProperty().addListener((observable, oldValue, newValue) -> updateBars());
        progressBar.widthProperty().addListener((observable, oldValue, newValue) -> {
            resetBars();
            updateBars();
        });
        progressBar.visibleProperty().addListener((observable, oldValue, newValue) -> {
            resetBars();
            updateBars();
        });
        progressBar.parentProperty().addListener((observable, oldValue, newValue) -> {
            resetBars();
            updateBars();
        });
        progressBar.sceneProperty().addListener((observable, oldValue, newValue) -> {
            resetBars();
            updateBars();
        });
        progressBar.animationSpeedProperty().addListener((observable, oldValue, newValue) -> {
            resetBars();
            updateBars();
        });
    }

    /**
     * Responsible for updating the progress bar state.
     * <p></p>
     * If it is indeterminate calls {@link #playIndeterminateAnimation()}, otherwise calls
     * {@link #resetBars()} and {@link #updateProgress()}.
     */
    protected void updateBars() {
        MFXProgressBar progressBar = getSkinnable();

        if (progressBar.isIndeterminate()) {
            playIndeterminateAnimation();
        } else {
            resetBars();
            updateProgress();
        }
    }

    /**
     * Responsible for clearing the indeterminate animation (stop, clear children and set to null), and
     * resetting the bars layout, scale and width properties.
     */
    protected void resetBars() {
        if (indeterminateAnimation != null) {
            indeterminateAnimation.stop();
            indeterminateAnimation.getChildren().clear();
            indeterminateAnimation = null;
        }

        bar1.setLayoutX(0);
        bar1.setScaleX(1.0);
        bar1.setWidth(0);
        bar2.setLayoutX(0);
        bar2.setScaleX(1.0);
        bar2.setWidth(0);
    }

    /**
     * Responsible for calculating the bar width according to the current progress
     * (so when the progress bar is not indeterminate).
     */
    protected void updateProgress() {
        MFXProgressBar progressBar = getSkinnable();

        double width = ((progressBar.getWidth()) * (progressBar.getProgress() * 100)) / 100;
        bar1.setWidth(width);
    }

    /**
     * If the indeterminate animation is already playing returns.
     * <p></p>
     * Responsible for building the indeterminate animation.
     */
    protected void playIndeterminateAnimation() {
        MFXProgressBar progressBar = getSkinnable();

        if (indeterminateAnimation != null) {
            return;
        }

        final double width = progressBar.getWidth() - (snappedLeftInset() + snappedRightInset());
        KeyFrame kf0 = new KeyFrame(Duration.ONE,
                new KeyValue(bar1.scaleXProperty(), 0.7),
                new KeyValue(bar1.layoutXProperty(), -width),
                new KeyValue(bar1.widthProperty(), width / 2),
                new KeyValue(bar2.layoutXProperty(), -width),
                new KeyValue(bar2.widthProperty(), width / 2)
        );
        KeyFrame kf1 = new KeyFrame(Duration.millis(700),
                new KeyValue(bar1.scaleXProperty(), 1.25, Interpolator.EASE_BOTH)
        );
        KeyFrame kf2 = new KeyFrame(Duration.millis(1300),
                new KeyValue(bar1.layoutXProperty(), width, Interpolator.LINEAR)
        );
        KeyFrame kf3 = new KeyFrame(Duration.millis(1100),
                new KeyValue(bar1.scaleXProperty(), 1.0, Interpolator.EASE_OUT)
        );
        KeyFrame kf4 = new KeyFrame(Duration.millis(1100),
                new KeyValue(bar2.layoutXProperty(), width * 2, Interpolator.LINEAR),
                new KeyValue(bar2.scaleXProperty(), 2, Interpolator.EASE_BOTH)
        );

        Timeline bar1Animation = new Timeline(kf0, kf1, kf2, kf3);
        Timeline bar2Animation = new Timeline(kf4);
        bar2Animation.setDelay(Duration.millis(1100));

        indeterminateAnimation = new ParallelTransition(bar1Animation, bar2Animation);
        indeterminateAnimation.setCycleCount(Timeline.INDEFINITE);
        indeterminateAnimation.setRate(progressBar.getAnimationSpeed());
        indeterminateAnimation.play();
    }

    /**
     * Responsible for building the track and the bars for the progress bar.
     */
    protected Rectangle buildRectangle(String styleClass) {
        MFXProgressBar progressBar = getSkinnable();

        Rectangle rectangle = new Rectangle();
        rectangle.getStyleClass().setAll(styleClass);
        rectangle.setStroke(Color.TRANSPARENT);
        rectangle.setStrokeLineCap(StrokeLineCap.ROUND);
        rectangle.setStrokeLineJoin(StrokeLineJoin.ROUND);
        rectangle.setStrokeType(StrokeType.INSIDE);
        rectangle.setStrokeWidth(0);
/*        rectangle.arcHeightProperty().bind(progressBar.bordersRadiusProperty());
        rectangle.arcWidthProperty().bind(progressBar.bordersRadiusProperty());*/
        return rectangle;
    }

    //================================================================================
    // OverrideMethods
    //================================================================================

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return Math.max(100, leftInset + bar1.prefWidth(getSkinnable().getWidth()) + rightInset);
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return Math.max(5, bar1.prefHeight(width)) + topInset + bottomInset;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (indeterminateAnimation != null) {
            indeterminateAnimation.stop();
            indeterminateAnimation.getChildren().clear();
            indeterminateAnimation = null;
        }
    }
}
