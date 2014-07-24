# Important Notes
## Building Notes
The project is meant to be built in a Java first then Scala manner with the exception of a few files. Primarily ScalaPlugins.java which is used for loading the Plugins written in Scala into the SezPoz-based TIPLPluginManager. A simple space entered into this file and save will cause it to recompile after the scala code has been compiled resolving all errors.
## Updating Libraries
### Updating Spark
The TIPL code itself is separated from Spark but involves the spark code in a number of key areas. 
- SGEJob sets up the path and includes reference to the jumbo jar file in the spark/lib folder, many tasks unrelated to Spark will not work if this is missing (to prevent library duplication)

