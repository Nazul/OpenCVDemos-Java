/*
 * Copyright 2015 Mario_Contreras.
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
 * 
 * Based on:
 * http://opencv-java-tutorials.readthedocs.org/en/latest/08-object-detection.html
 * https://github.com/opencv-java/object-detection
 * http://mathalope.co.uk/2015/05/14/opencv-tutorial-real-time-object-tracking-without-color/
 * 
 */
package opencvdemos;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

/**
 *
 * @author Mario_Contreras
 */
public class BallGame extends javax.swing.JFrame {

    // A timer for acquiring the video stream
    private ScheduledExecutorService timer;
    // The OpenCV object that realizes the video capture
    private VideoCapture capture;
    // A flag to change the button behavior
    private boolean cameraActive;
    // The ball object
    Ball b;
    // Flag to determinate if the ball has changed its course
    boolean ballChanged;

    // Ball class
    private class Ball {
        int x = 100, y = 100;
        int dx = 5, dy = 5;
        int w, h;
        int r = 15;
        
        Ball(int w, int h) {
            this.w = w;
            this.h = h;
        }
        
        void move() {
            x += dx;
            y += dy;
            if(x < r) dx *= -1;
            if(x >= w - r) dx *= -1;
            if(y < r) dy *= -1;
            if(y >= h - r) dy *= -1;
        }
    }
    

    /**
     * Creates new form BallGame
     */
    public BallGame() {
        initComponents();
        // Center
        this.setLocationRelativeTo(null);
        // load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        // Init components
        this.capture = new VideoCapture();
        this.cameraActive = false;
        this.hsvCurrentValues.setText("");
        this.b = new Ball(currentFrame.getWidth(), currentFrame.getHeight());
    }

    private Mat findAndDrawObjects(Mat maskedImage, Mat frame) {
        // Init
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();

        // Find contours
        Imgproc.findContours(maskedImage, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);

        // If any contour exist...
        if (hierarchy.size().height > 0 && hierarchy.size().width > 0) {
            // for each contour, display it in blue
            for (int idx = 0; idx >= 0; idx = (int) hierarchy.get(0, idx)[0]) {
                Imgproc.drawContours(frame, contours, idx, new Scalar(250, 0, 0));
            }
        }

        return frame;
    }

    private Image mat2Image(Mat frame) {
        // Create a temporary buffer
        MatOfByte buffer = new MatOfByte();
        // Encode the frame in the buffer, according to the PNG format
        Imgcodecs.imencode(".png", frame, buffer);
        // Build and return an Image created from the image encoded in the buffer
        // Return new BufferedImage(new ByteArrayInputStream(buffer.toArray()));
        BufferedImage img = null;
        try {
            img = ImageIO.read(new ByteArrayInputStream(buffer.toArray()));
        }
        catch (Exception e) {
            // log the error
            System.err.println("Exception while converting frame: " + e);
        }
        return img;
    }

    private Image grabFrame() {
        // Init everything
        Image imageToShow = null;
        Mat frame = new Mat();

        // Check if the capture is open
        if (this.capture.isOpened()) {
            try {
                // Read the current frame
                this.capture.read(frame);
                // Flip image for easy object manipulation
                Core.flip(frame, frame, 1);

                // If the frame is not empty, process it
                if (!frame.empty()) {
                    // Init
                    Mat blurredImage = new Mat();
                    Mat hsvImage = new Mat();
                    Mat mask = new Mat();
                    Mat morphOutput = new Mat();

                    // Remove some noise
                    Imgproc.blur(frame, blurredImage, new Size(7, 7));

                    // Convert the frame to HSV
                    Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);

                    // Get thresholding values from the UI
                    // Remember: H ranges 0-180, S and V range 0-255
                    Scalar minValues = new Scalar(this.hueStart.getValue(), this.saturationStart.getValue(), this.valueStart.getValue());
                    Scalar maxValues = new Scalar(this.hueStop.getValue(), this.saturationStop.getValue(), this.valueStop.getValue());

                    // Show the current selected HSV range
                    String valuesToPrint = "Hue range: " + minValues.val[0] + "-" + maxValues.val[0]
                                    + ". Sat. range: " + minValues.val[1] + "-" + maxValues.val[1] + ". Value range: "
                                    + minValues.val[2] + "-" + maxValues.val[2];
                    hsvCurrentValues.setText(valuesToPrint);

                    // Threshold HSV image to select object
                    Core.inRange(hsvImage, minValues, maxValues, mask);
                    // Show the partial output
                    maskImage.getGraphics().drawImage(this.mat2Image(mask), 0, 0, 205, 154, null);

                    // Morphological operators
                    // Dilate with large element, erode with small ones
                    Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
                    Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));

                    Imgproc.erode(mask, morphOutput, erodeElement);
                    Imgproc.erode(mask, morphOutput, erodeElement);

                    Imgproc.dilate(mask, morphOutput, dilateElement);
                    Imgproc.dilate(mask, morphOutput, dilateElement);

                    // Show the partial output
                    morphImage.getGraphics().drawImage(this.mat2Image(morphOutput), 0, 0, 205, 154, null);

                    // Find the object(s) contours and show them
                    frame = this.findAndDrawObjects(morphOutput, frame);

                    // Calculate centers and move ball
                    Mat temp = new Mat();
                    morphOutput.copyTo(temp);
                    List<MatOfPoint> contours = new ArrayList<>();
                    Imgproc.findContours(temp, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                    for(int i=0; i< contours.size(); i++) {
                        Rect objectBoundingRectangle = Imgproc.boundingRect(contours.get(i));
                        int x = objectBoundingRectangle.x + objectBoundingRectangle.width / 2;
        		int y = objectBoundingRectangle.y + objectBoundingRectangle.height / 2;
                        
                        // Move ball
                        if(!ballChanged) {
                            if(b.x > objectBoundingRectangle.x && b.x < objectBoundingRectangle.x + objectBoundingRectangle.width &&
                               b.y > objectBoundingRectangle.y && b.y < objectBoundingRectangle.y + objectBoundingRectangle.height) {
                                b.dx = -b.dx;
                                b.dy = -b.dy;
                                ballChanged = true;
                            }
                        }

                        // Show crosshair
                        Imgproc.circle(frame, new Point(x,y), 20, new Scalar(0, 255, 0), 2);
                        Imgproc.line(frame, new Point(x, y), new Point(x, y - 25), new Scalar(0, 255, 0), 2);
                        Imgproc.line(frame, new Point(x, y), new Point(x, y + 25), new Scalar(0, 255, 0), 2);
                        Imgproc.line(frame, new Point(x, y), new Point(x - 25, y), new Scalar(0, 255, 0), 2);
                        Imgproc.line(frame, new Point(x, y), new Point(x + 25, y), new Scalar(0, 255, 0), 2);
                        Imgproc.putText(frame, "Tracking object at (" + x + "," + y + ")", new Point(x, y), 1, 1, new Scalar(255, 0, 0), 2);
                    }
                    ballChanged = false;

                    // Move and draw the ball
                    if(b.dx < 0) b.dx = ballSpeed.getValue() * -1; else b.dx = ballSpeed.getValue();
                    if(b.dy < 0) b.dy = ballSpeed.getValue() * -1; else b.dy = ballSpeed.getValue();
                    b.move();
                    Imgproc.circle(frame, new Point(b.x, b.y), b.r, new Scalar(255, 0, 255), -1);

                    // convert the Mat object (OpenCV) to Image (Java AWT)
                    imageToShow = mat2Image(frame);
                }

            }
            catch (Exception e) {
                // log the error
                System.err.println("Exception during the frame elaboration: " + e);
            }
        }

        return imageToShow;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        currentFrame = new javax.swing.JPanel();
        maskImage = new javax.swing.JPanel();
        morphImage = new javax.swing.JPanel();
        ballSpeed = new javax.swing.JSlider();
        btnStart = new javax.swing.JButton();
        hueStart = new javax.swing.JSlider();
        hueStop = new javax.swing.JSlider();
        saturationStart = new javax.swing.JSlider();
        saturationStop = new javax.swing.JSlider();
        valueStart = new javax.swing.JSlider();
        valueStop = new javax.swing.JSlider();
        hsvCurrentValues = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("OpenCV Test - Ball Game");
        setResizable(false);
        setSize(new java.awt.Dimension(800, 600));

        currentFrame.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        currentFrame.setPreferredSize(new java.awt.Dimension(320, 240));

        javax.swing.GroupLayout currentFrameLayout = new javax.swing.GroupLayout(currentFrame);
        currentFrame.setLayout(currentFrameLayout);
        currentFrameLayout.setHorizontalGroup(
            currentFrameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 636, Short.MAX_VALUE)
        );
        currentFrameLayout.setVerticalGroup(
            currentFrameLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 476, Short.MAX_VALUE)
        );

        maskImage.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        maskImage.setPreferredSize(new java.awt.Dimension(320, 240));

        javax.swing.GroupLayout maskImageLayout = new javax.swing.GroupLayout(maskImage);
        maskImage.setLayout(maskImageLayout);
        maskImageLayout.setHorizontalGroup(
            maskImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 201, Short.MAX_VALUE)
        );
        maskImageLayout.setVerticalGroup(
            maskImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 150, Short.MAX_VALUE)
        );

        morphImage.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        morphImage.setPreferredSize(new java.awt.Dimension(320, 240));

        javax.swing.GroupLayout morphImageLayout = new javax.swing.GroupLayout(morphImage);
        morphImage.setLayout(morphImageLayout);
        morphImageLayout.setHorizontalGroup(
            morphImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 201, Short.MAX_VALUE)
        );
        morphImageLayout.setVerticalGroup(
            morphImageLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );

        ballSpeed.setMaximum(50);
        ballSpeed.setMinimum(1);
        ballSpeed.setValue(5);

        btnStart.setText("Start Camera");
        btnStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStartActionPerformed(evt);
            }
        });

        hueStart.setMaximum(180);
        hueStart.setValue(20);

        hueStop.setMaximum(180);

        saturationStart.setMaximum(255);
        saturationStart.setValue(60);

        saturationStop.setMaximum(255);
        saturationStop.setValue(200);

        valueStart.setMaximum(255);

        valueStop.setMaximum(255);
        valueStop.setValue(255);

        hsvCurrentValues.setText("[hsvCurrentValues]");
        hsvCurrentValues.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(maskImage, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(morphImage, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(hsvCurrentValues, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(0, 0, 0)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(valueStart, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(saturationStop, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(saturationStart, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hueStop, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(hueStart, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(ballSpeed, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnStart))
                            .addComponent(valueStop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(currentFrame, javax.swing.GroupLayout.PREFERRED_SIZE, 640, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(currentFrame, javax.swing.GroupLayout.PREFERRED_SIZE, 480, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnStart)
                            .addComponent(ballSpeed, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, 0)
                        .addComponent(hueStart, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(hueStop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(saturationStart, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(saturationStop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(valueStart, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(valueStop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(maskImage, javax.swing.GroupLayout.DEFAULT_SIZE, 154, Short.MAX_VALUE)
                            .addComponent(morphImage, javax.swing.GroupLayout.DEFAULT_SIZE, 154, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(hsvCurrentValues, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStartActionPerformed
        if (!this.cameraActive) {
            // start the video capture
            this.capture.open(0);

            // is the video stream available?
            if (this.capture.isOpened()) {
                this.cameraActive = true;

                // grab a frame every 33 ms (30 frames/sec)
                Runnable frameGrabber = new Runnable() {
                    @Override
                    public void run() {
                        Image imageToShow = grabFrame();
                        //currentFrame.setImage(imageToShow);
                        currentFrame.getGraphics().drawImage(imageToShow, 0, 0, null);
                    }
                };

                this.timer = Executors.newSingleThreadScheduledExecutor();
                this.timer.scheduleAtFixedRate(frameGrabber, 0, 33, TimeUnit.MILLISECONDS);

                // update the button content
                this.btnStart.setText("Stop Camera");
            }
            else {
                // log the error
                System.err.println("Impossible to open the camera connection...");
            }
        }
        else {
            // the camera is not active at this point
            this.cameraActive = false;
            // update again the button content
            this.btnStart.setText("Start Camera");

            // stop the timer
            try {
                this.timer.shutdown();
                this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e) {
                // log the exception
                System.err.println("Exception in stopping the frame capture, trying to release the camera now... " + e);
            }

            // release the camera
            this.capture.release();
            // clean the frame
            //this.currentFrame.setImage(null);
            this.currentFrame.getGraphics().clearRect(0, 0, currentFrame.getWidth(), currentFrame.getHeight());
            this.currentFrame.getBorder().paintBorder(currentFrame, currentFrame.getGraphics(), 0, 0, currentFrame.getWidth(), currentFrame.getHeight());
            this.maskImage.getGraphics().clearRect(0, 0, maskImage.getWidth(), maskImage.getHeight());
            this.maskImage.getBorder().paintBorder(maskImage, maskImage.getGraphics(), 0, 0, maskImage.getWidth(), maskImage.getHeight());
            this.morphImage.getGraphics().clearRect(0, 0, morphImage.getWidth(), morphImage.getHeight());
            this.morphImage.getBorder().paintBorder(morphImage, morphImage.getGraphics(), 0, 0, morphImage.getWidth(), morphImage.getHeight());
        }
    }//GEN-LAST:event_btnStartActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(BallGame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(BallGame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(BallGame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(BallGame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new BallGame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSlider ballSpeed;
    private javax.swing.JButton btnStart;
    private javax.swing.JPanel currentFrame;
    private javax.swing.JLabel hsvCurrentValues;
    private javax.swing.JSlider hueStart;
    private javax.swing.JSlider hueStop;
    private javax.swing.JPanel maskImage;
    private javax.swing.JPanel morphImage;
    private javax.swing.JSlider saturationStart;
    private javax.swing.JSlider saturationStop;
    private javax.swing.JSlider valueStart;
    private javax.swing.JSlider valueStop;
    // End of variables declaration//GEN-END:variables
}
