<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:tns="urn://x-artefacts-smev-gov-ru/services/service-adapter/types">
	<xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes"/>
	<xsl:template match="/">
		<ClientMessage xmlns="urn://x-artefacts-smev-gov-ru/services/service-adapter/types">
			<itSystem>
				<xsl:value-of select="tns:QueryResult/tns:smevMetadata/tns:Sender"/>
			</itSystem>
			<RequestMessage>
				<RequestMetadata>
					<clientId>
						<xsl:value-of select="tns:QueryResult/tns:Message/tns:RequestMetadata/tns:clientId"/>
					</clientId>
					<xsl:if test="tns:QueryResult/tns:Message/tns:RequestMetadata/tns:testMessage">
						<testMessage>
							<xsl:value-of select="tns:QueryResult/tns:Message/tns:RequestMetadata/tns:testMessage"/>
						</testMessage>
					</xsl:if>
				</RequestMetadata>
				<RequestContent>
					<content>
						<xsl:copy-of select="tns:QueryResult/tns:Message/tns:RequestContent/tns:content/tns:MessagePrimaryContent"/>
					</content>
				</RequestContent>
			</RequestMessage>
		</ClientMessage>
	</xsl:template>
</xsl:stylesheet>
