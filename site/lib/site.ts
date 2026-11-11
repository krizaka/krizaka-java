/* Where the site is, and where it points. Structure only — the words are messages. */

export const SITE_URL = "https://java.krizaka.com";
export const KRIZAKA_URL = "https://www.krizaka.com";
export const REPOSITORY_URL = "https://github.com/krizaka/krizaka-java";
export const MAVEN_CENTRAL_URL = "https://central.sonatype.com/namespace/com.krizaka";
/** An artifact's page on Maven Central. */
export const mavenArtifactUrl = (artifactId: string) => `https://central.sonatype.com/artifact/com.krizaka/${artifactId}`;
