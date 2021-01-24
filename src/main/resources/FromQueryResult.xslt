<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:h="urn://x-artefacts-smev-gov-ru/services/service-adapter/types" xmlns:ns2="urn://x-artefacts-smev-gov-ru/services/service-adapter/types/faults" xmlns:ap="urn://x-artefacts-smev-gov-ru/fsor01/types/faults">
	<xsl:output method="xml" encoding="UTF-8" indent="yes"/>
	<xsl:template match="/">
		<xsl:apply-templates select="h:QueryResult/h:Message/h:ResponseContent/h:content/h:MessagePrimaryContent"/>
		<xsl:apply-templates select="h:QueryResult/h:Message[@xsi:type='ErrorMessage']"/>
		<xsl:apply-templates select="//h:rejects"/>
		<xsl:apply-templates select="//h:status"/>
	</xsl:template>
	<xsl:template match="h:QueryResult/h:Message/h:ResponseContent/h:content/h:MessagePrimaryContent">
		<xsl:copy-of select="./child::*"/>
	</xsl:template>
	<xsl:template match="h:QueryResult/h:Message[@xsi:type='ErrorMessage']">
			<ap:apAdapterFault>
				<ap:source><xsl:value-of select="//h:type"/></ap:source>
				<ap:code><xsl:value-of select="//ns2:code"/></ap:code>
				<ap:description><xsl:value-of select="//ns2:description"/></ap:description>
			</ap:apAdapterFault>
	</xsl:template>
	<xsl:template match="//h:rejects">
		<ap:apAdapterFault>
			<ap:source>SERVER</ap:source>
			<ap:code><xsl:value-of select="//h:code"/></ap:code>
			<ap:description><xsl:value-of select="//h:description"/></ap:description>
		</ap:apAdapterFault>
	</xsl:template>
	<xsl:template match="//h:status">
		<ap:apAdapterFault>
			<ap:source>SERVER</ap:source>
			<ap:code>Status</ap:code>
			<ap:description><xsl:value-of select="//h:description"/></ap:description>
		</ap:apAdapterFault>
	</xsl:template>
</xsl:stylesheet>
