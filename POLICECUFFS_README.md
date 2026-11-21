# Police Cuffs — mod NeoForge 1.21.11
Version 0.1.2 — compilado para NeoForge 21.11.45.

## Recetas
**Receta normal (mesa 3x3):**
```
I . I
. C .
I . I
```
I = lingote de hierro; C = cadena de hierro; . = hueco vacío.

**Receta alternativa:** cuatro lingotes de hierro y una cadena de hierro, en cualquier posición de una mesa 3x3.

La receta aparece en el libro del juego al obtener una cadena de hierro en el inventario (si ya tienes una, sácala y vuelve a recogerla).

## JEI en Minecraft 1.21.11
En 1.21.2+, instala JEI tanto en cliente como en servidor NeoForge. 
JEI solo en cliente puede no mostrar recetas del servidor.

## Diagnóstico
Al arrancar el servidor, busca `Comprobacion de recetas Police Cuffs` en `logs/latest.log`.
Debe indicar `handcuffs=OK, handcuffs_flexible=OK`.
Si no aparece esta línea, el mod puede no estar cargando en el servidor.
Para verificar el registro de la receta como operador:
`/recipe give <jugador> policecuffs:handcuffs`

## Instalación
Reemplaza el JAR 0.1.1 por el JAR 0.1.2 tanto en servidor como cliente. No instales dos versiones juntas.
El mod aún requiere pruebas en un mundo de prueba antes de usarlo en producción.
