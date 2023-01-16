# VideoRecordingApp

This repository consists of a java application that records videos from USB-connected webcams based on a configurable scheduler. A graphical user interface enables easy termination of ongoing recordings. The app assumes a Linux OS; however the command that accesses webcams to start and end recordings can be replaced with alternatives compatible with a different OS. 

## Installation Guide:
This application is intended to be cloned and run inside a Singularity build, but can also be run manually directly from the command line. After cloning the repository, the following steps can be followed to manually launch the app:

1. Install the following packages on the local machine:
* Java 11
* ffmpeg 
* video4linux2

2.  Adjust the addresses of each webcam in the cameraLocations.yml, and set desired recording times in the recordTimes.yml. The address of all USB webcams can be listed by opening a terminal and running `v4l2-ctl --list-devices`. More detailed information about an individual webcam (including it's codec, resolution and frame-rate capabilities) can be obtained by running `v4l2-ctl -d /dev/video# --list-formats-ext`

3. Currently, the following directory structure exists for writing videos, log and metadata files: `/media/DATA/`. 
Adjust the _sp_ parameter in the [MainControlLoop.java](https://github.com/Weill-Neurohub-OPTiMaL/OPTiMaL_Cameras/blob/main/src/main/java/MainControlLoop.java) script if a different directory structure is needed.

4. From inside the base directory level of the repository, run the following: **java -jar /OPTiMaL_Cameras/out/artifacts/OPTiMal_Cameras/OPTiMaL_Cameras.jar**


## Output Structure:
For every individual recording, several files will be generated and stored in a folder that is created at the time the video is scheduled to record. The folder has the naming convention video#_datetime, where the datetime has **millisecond-level precision**. Within that folder are the following files:
* **Video files**: these have the naming convention video#_datetime.avi (where the # refers to the computer address of the webcam), with **second-level precision** from the time the file is first created. There are as many individual video files as total_recording_duration/video_segment_length. **NOTE**: Sometimes there will be 1 extra video file created that is empty. This has to do with OS job scheduling. Simply ignore this empty video file if it exists.
* **A metadata file**: this has the naming convention video#_metadata.json. This stores information about the camera configuration, recording start and end times (at **millisecond-level precision**), and other information about the recording process.
* **A standardError log file**: this has the naming convention errOut_video#_datetime.log, which contains time stamps at **millisecond-level precision** for each frame of the video. There is also other information about the video frames that ffmpeg generates.
* **A standardOut log file**: this has the naming convention stdOut_video#_datetime.log which for now is empty and can be ignored.


## Citing and Authorship 
This repository was written by [Gabrielle Strandquist](https://github.com/strandquistg) and is overseen by [Jeffrey Herron](https://neurosurgery.uw.edu/bio/jeffrey-herron-phd). 

## Funding 
This work was supported by funding from the Weill Neurohub, the National Institutes of Health (UH3NS100544), and the National Science Foundation (DGE-2140004). 
