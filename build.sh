#!/bin/sh
# Compila el mod. Necesita Java 17 e internet (primera vez tarda varios minutos).
./gradlew build && echo && echo "LISTO: tu mod esta en build/libs/" && ls build/libs
