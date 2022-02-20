<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fssp="urn://x-artifacts-fssp-ru/mvv/smev3/application-documents/1.1.1" xmlns:c="urn://x-artifacts-fssp-ru/mvv/smev3/container/1.1.0" xmlns:att="urn://x-artifacts-fssp-ru/mvv/smev3/attachments/1.1.0">
	<xsl:output method="xml" encoding="UTF-8" indent="yes"/>
	<xsl:param name="DocKey"/>
	<xsl:template match="/">
		<xsl:apply-templates select="//fssp:ApplicationDocumentsRequest"/>
	</xsl:template>
	<xsl:template match="//fssp:ApplicationDocumentsRequest">
		<fssp:ApplicationDocumentsRequest>
			<c:ID>
				<xsl:value-of select="./c:ID"/>
			</c:ID>
			<c:Date>
				<xsl:value-of select="./c:Date"/>
			</c:Date>
			<c:SenderID>
				<xsl:value-of select="./c:SenderID"/>
			</c:SenderID>
			<c:SenderDepartmentCode>
				<xsl:value-of select="./c:SenderDepartmentCode"/>
			</c:SenderDepartmentCode>
			<c:ReceiverID>
				<xsl:value-of select="./c:ReceiverID"/>
			</c:ReceiverID>
			<xsl:apply-templates select="//c:Document"/>
		</fssp:ApplicationDocumentsRequest>
	</xsl:template>
	<xsl:template match="//c:Document">
		<xsl:if test="./c:IncomingDocKey/text()=$DocKey">
			<xsl:copy-of select="."/>
		</xsl:if>
	</xsl:template>
</xsl:stylesheet>
