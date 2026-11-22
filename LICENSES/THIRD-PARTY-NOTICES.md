# Callsmith distribution notices

The adjacent COMPONENTS.txt lists the exact application, Java runtime, and
Maven libraries staged in this image. License texts are grouped below this
directory. The linked upstream sources identify the shipped revisions.

| Component | License material and source |
| --- | --- |
| Callsmith | LICENSE and NOTICE in this directory. Source is available from [Mirakinetix](https://github.com/mirakinetix/callsmithOpenSource). |
| Lucide fallback icons | LUCIDE-LICENSE.txt includes the Lucide and inherited Feather terms. [Lucide source and license](https://github.com/lucide-icons/lucide/blob/main/LICENSE). |
| Callsmith artwork | BRANDING.md records the Mirakinetix asset pack, provenance and attribution. The pack supplied no standalone redistribution license. Confirm authority to distribute the approved artwork before public binary release. |
| OpenJFX 25 | OpenJFX-25 contains GPLv2 with Classpath Exception material and the graphics module notices (JPEG, Mesa and PipeWire). Source tag [25-ga](https://github.com/openjdk/jfx/tree/1718dc767543023a5466304c09fb2d39711cb1a8). See ADDITIONAL_LICENSE_INFO for the independent components covered by other terms. |
| Jackson | The jackson-annotations, jackson-core and jackson-databind directories contain the Apache 2.0 material extracted from their release JARs. The Core directory also includes the JAR's bundled FastDoubleParser and Schubfach terms. Source tags [Annotations 2.21](https://github.com/FasterXML/jackson-annotations/tree/jackson-annotations-2.21), [Core 2.21.8](https://github.com/FasterXML/jackson-core/tree/jackson-core-2.21.8) and [Databind 2.21.8](https://github.com/FasterXML/jackson-databind/tree/jackson-databind-2.21.8). |
| SQLite JDBC 3.53.4.0 and embedded SQLite 3.53.4 | sqlite-jdbc contains the Apache 2.0 and Zentus BSD terms extracted from the JAR. SQLite core is [public domain](https://www.sqlite.org/copyright.html). [SQLite JDBC source](https://github.com/xerial/sqlite-jdbc/tree/3.53.4.0), [embedded SQLite version](https://www.sqlite.org/releaselog/3_53_4.html). |
| Eclipse Temurin 25.0.5.1+2 | The bundled runtime's legal directory retains runtime module licenses and third-party notices. [Exact Temurin release and source archive](https://github.com/adoptium/temurin25-binaries/releases/tag/jdk-25.0.5.1%2B2). |

Before publishing a binary, provide equivalent access to its corresponding
GPLv2 sources from the same distribution location (or use another permitted
GPLv2 section 3 method). These source references help identify the materials
for that step. Verify the exact OpenJFX and Temurin source bundles and any
needed build material with the release owner. See
[GPLv2 section 3](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html#section3).
