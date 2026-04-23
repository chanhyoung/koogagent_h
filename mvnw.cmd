@echo off
REM Use local Maven installation
IF NOT "%MAVEN_HOME%"=="" (
    "%MAVEN_HOME%\bin\mvn" %*
) ELSE (
    mvn %*
)
