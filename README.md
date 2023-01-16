# VideoRecordingApp

## Overview:
This application records videos to support an at-home multi-modal data collection platform to support neuromodulation research for people with neurodegenerative diseases. Recorded videos are processed to extract kinematic trajectories from joint position estimates in 2D or 3D. The kinematic data are time-aligned with neural and wearable-sensor recordings to allow clinicians and researchers to analyze movement quality and symptom severity from remote. 

The code consists of a java application that records videos from USB-connected webcams based on a configurable scheduler. A graphical user interface enables easy termination of ongoing recordings. The app assumes a Linux OS; however the command that accesses webcams to start and end recordings can be replaced with alternatives compatible with a different OS. 

## Installation Guide:
After cloning the repository, the following steps will manually launch the app:

1. Install the following packages on a PC:
* Java 11
* ffmpeg 
* video4linux2

2.  Adjust the addresses of each webcam in the cameraLocations.yml, and set desired recording times in the recordTimes.yml. The address of all USB webcams can be listed by opening a terminal and running `v4l2-ctl --list-devices`. More detailed information about an individual webcam (including it's codec, resolution and frame-rate capabilities) can be obtained by running `v4l2-ctl -d /dev/video# --list-formats-ext`

3. Currently, the following directory structure exists for writing videos, log and metadata files: `/media/DATA/`. 
Adjust the _sp_ parameter in the [MainControlLoop.java](https://github.com/Weill-Neurohub-OPTiMaL/OPTiMaL_Cameras/blob/main/src/main/java/MainControlLoop.java) script if a different directory structure is needed.

4. From inside the base directory level of the repository, run the following: **java -jar /OPTiMaL_Cameras/out/artifacts/OPTiMal_Cameras/OPTiMaL_Cameras.jar**


## Output Structure:
For every individual recording, several files will be generated and stored in a folder that is created at the time the video is scheduled to record. The folder has the naming convention video#_datetime, where the datetime has **millisecond-level precision**. Within that folder are the following files:
* **Video files**: these have the naming convention video#_datetime.avi (where the # refers to the computer address of the webcam), with **second-level precision** from the time the file is first created. There are as many individual video files as total_recording_duration/video_segment_length. **NOTE**: Sometimes there will be 1 extra video file created that is empty. This has to do with OS job scheduling and the file can be deleted or ignored if it exists.
* **A metadata file**: this has the naming convention video#_metadata.json. This stores information about the camera configuration, recording start and end times (at **millisecond-level precision**), and other information about the recording process.
* **A standardError log file**: this has the naming convention errOut_video#_datetime.log, which contains time stamps at **millisecond-level precision** for each frame of the video. There is also other information about the video frames that ffmpeg generates.
* **A standardOut log file**: this has the naming convention stdOut_video#_datetime.log which for now is empty and can be ignored.


## Citing and Authorship 
If you use our code, please cite our Journal of Visualized Experiments [paper](https://www.jove.com/methods-collections/2119). 

This repository was written by [Gabrielle Strandquist](https://github.com/strandquistg) and is overseen by [Jeffrey Herron](https://neurosurgery.uw.edu/bio/jeffrey-herron-phd). 

## Funding 
This work was supported by funding from the Weill Neurohub, the National Institutes of Health (UH3NS100544), and the National Science Foundation (DGE-2140004). 
