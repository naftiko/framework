# CLI for Naftiko

This is the source code of the CLI for Naftiko.

## Description

The goal of this CLI is to simplify configuration and validation. While everything can be done manually, the CLI provides helper commands.

## Getting started
### Prerequisites
* You must have java installed.
* You must have mvn installed and use it as package manager.
  * For MacOS we suggest to use brew installer
  ```
  brew install maven
  ```
### Dependencies
* You must install the project dependencies.
```
mvn clean install
```
It should generate several jar files in the target folder. One of them should be cli.jar.
### Execute
* To compile the source code:
  ```
  mvn clean package
  ```
* To generate a capability configuration file, execute the following command:
  ```
  java -jar target/cli.jar create capability
  ```
  It should create a capability configuration file at the root of the project.
* To validate a capability configuration file from a json schema file:
  ```
  java -jar target/cli.jar validate path_to_your_configuratio_file.yaml_or_json path_to_the_json_scheam_file.json
  ```
## Execution command and aliases
* To avoid typing java -jar target/cli.jar a target/appassembler/bin/naftiko script is generated when compiling. If you want this script to be available everywhere, add an alias to your ~/.bashrc or ~/.zshrc file:
  ```
  alias naftiko='path_to_your_framework_project_folder/target/appassembler/bin/naftiko'
  ```
  Then you can use
  ```
  naftiko create capability
  naftiko validate path_to_your_configuratio_file.yaml_or_json path_to_the_json_scheam_file.json
  ```
* Shorter aliases are also available:
  ```
  naftiko create capability
  naftiko c cap
  naftiko cr cap
  ```
  ```
  naftiko validate path_to_your_configuratio_file.yaml_or_json path_to_the_json_scheam_file.json
  naftiko v path_to_your_configuratio_file.yaml_or_json path_to_the_json_scheam_file.json
  naftiko val path_to_your_configuratio_file.yaml_or_json path_to_the_json_scheam_file.json
  ```
  