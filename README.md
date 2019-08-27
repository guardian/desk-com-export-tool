# desk-com-export-tool
Command line tool supporting migration from desk.com to Sales Force Service Cloud

Usage
=====

To run the export use the following command:

```$bash
 sbt "application/run -u <<desk.com username>> -p <<desk.com password>>" -o <<aws profile>> s3://<<s3 bucket>>/<<s3 path>>"
```

Use the -s command line option to "scrub" personal data from the file for test purposes:

```$bash
 sbt "application/run -s -u <<desk.com username>> -p <<desk.com password>>" -o <<aws profile>> s3://<<s3 bucket>>/<<s3 path>>"
```

For all command line options run the following:

```$bash
 sbt "application/run -h"
```

To build an executable jar of the application run:
```$bash
sbt application/assembly
```

Run the jar as follows:
```$bash
 java -jar application/target/scala-2.12/application-assembly-0.1.0-SNAPSHOT.jar -s -u <<desk.com username>> -p <<desk.com password>>" -o <<aws profile>> s3://<<s3 bucket>>/<<s3 path>>
```