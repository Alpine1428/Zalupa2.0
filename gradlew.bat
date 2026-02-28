@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
set CLASSPATH=%DIRNAME%\gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME (set JAVA_EXE=%JAVA_HOME%/bin/java.exe) else (set JAVA_EXE=java.exe)
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%~n0" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
