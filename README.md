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
