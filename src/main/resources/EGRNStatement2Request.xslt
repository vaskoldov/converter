<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="no"/>
	<xsl:param name="regionCode"/>
	<xsl:param name="actionCode"/>
	<xsl:param name="fileName"/>
	<xsl:param name="clientID"/>
	<xsl:template match="/">
		<req:Request xmlns:req="urn://x-artefacts-rosreestr-gov-ru/virtual-services/egrn-statement/1.1.2">
			<req:region>
			<xsl:value-of select="$regionCode"/>
			</req:region>
			<req:externalNumber>
			<xsl:value-of select="$clientID"/>
			</req:externalNumber>
			<req:senderType>Vedomstvo</req:senderType>
			<req:actionCode>
			<xsl:value-of select="$actionCode"/>
			</req:actionCode>
			<req:Attachment>
				<req:IsMTOMAttachmentContent>true</req:IsMTOMAttachmentContent>
				<req:RequestDescription>
					<req:IsUnstructuredFormat>false</req:IsUnstructuredFormat>
					<req:IsZippedPacket>true</req:IsZippedPacket>
					<req:fileName>request.xml</req:fileName>
				</req:RequestDescription>
				<req:Statement>
					<req:IsUnstructuredFormat>false</req:IsUnstructuredFormat>
					<req:IsZippedPacket>true</req:IsZippedPacket>
					<req:fileName><xsl:value-of select="$fileName"/></req:fileName>
				</req:Statement>
				<req:File>
					<req:IsUnstructuredFormat>true</req:IsUnstructuredFormat>
					<req:IsZippedPacket>true</req:IsZippedPacket>
					<req:fileName><xsl:value-of select="$fileName"/>.sig</req:fileName>
				</req:File>
				<req:File>
					<req:IsUnstructuredFormat>true</req:IsUnstructuredFormat>
					<req:IsZippedPacket>true</req:IsZippedPacket>
					<req:fileName>request.xml.sig</req:fileName>
				</req:File>
			</req:Attachment>
		</req:Request>
	</xsl:template>
</xsl:stylesheet>
