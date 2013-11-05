# compiling
    mvn package

# create eclipse project files
    mvn eclipse:clean eclipse:eclipse

# running
    java -cp target/modelling-0.0.1-SNAPSHOT.jar ron.MergeDataFiles

This project uses mallet to do topic modelling on source code changelists
