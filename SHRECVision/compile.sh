#!/bin/bash
#
# This script runs the ant java compilation tool, and uses build.xml
# to link the opencv jar file and native library at run time.
#
ant -DocvJarDir=/home/pi/opencv/build/bin -DocvLibDir=/home/pi/opencv/build/lib
