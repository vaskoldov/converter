<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="no"/>
	<xsl:param name="fileName"/>
	<xsl:template match="/">
		<request xmlns="http://rosreestr.ru/services/v0.26/TRequest">
			<statementFile>
				<fileName><xsl:value-of select="$fileName"/></fileName>
			</statementFile>
			<file>
				<fileName><xsl:value-of select="$fileName"/>.sig</fileName>
			</file>
			<requestType>111300003000</requestType>
		</request>
	</xsl:template>
</xsl:stylesheet>
