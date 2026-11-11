@echo off
REM Compila el mod. Necesita Java 17 e internet (la primera vez tarda varios minutos).
call gradlew.bat build
if errorlevel 1 (echo ERROR en la compilacion & pause & exit /b 1)
echo.
echo LISTO: tu mod esta en la carpeta build\libs\
dir build\libs
pause
