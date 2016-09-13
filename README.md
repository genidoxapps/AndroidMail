# AndroidMail
It is not possible to build the ASOP Applications using eclipse/as directly. They use advanced features for building which can not be handled directly using the eclipse/as build process. Also the projects are not stand-alone but have cross-dependencies (see below)

The only straight solution for building the EMail (and all other) ASOP Applications is to check out the whole Android Source Tree and then use the build in build process using the make files. You can build the EMail application using "make EMail" in the folder where you checked out the source tree. You might need to set up a build environment first.

This project extracts code from [https://github.com/RaghavSood/CompilingAndroidMail](https://github.com/RaghavSood/CompilingAndroidMail) and make it compile successfully with gradle. 
