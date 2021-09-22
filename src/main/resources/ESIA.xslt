<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format" xmlns:directive="urn://x-artefacts-smev-gov-ru/services/message-exchange/types/directive/1.3" xmlns:tns="urn://x-artefacts-smev-gov-ru/services/service-adapter/types">
	<xsl:output method="xml" encoding="UTF-8"/>
	<xsl:param name="ClientID"/>
	<xsl:template match="/">
		<tns:ClientMessage xmlns:tns="urn://x-artefacts-smev-gov-ru/services/service-adapter/types">
			<tns:itSystem>FSOR01_3S</tns:itSystem>
			<tns:RequestMessage>
				<tns:RequestMetadata>
					<tns:clientId>
						<xsl:value-of select="$ClientID"/>
					</tns:clientId>
					<tns:RoutingInformation>
						<tns:DynamicRouting>
							<tns:DynamicValue>ISIA01001</tns:DynamicValue>
						</tns:DynamicRouting>
						<tns:RegistryRouting>
							<xsl:for-each select="//directive:RegistryRecord">
								<tns:RegistryRecordRouting>
									<tns:RecordId>
										<xsl:value-of select="position()"/>
									</tns:RecordId>
									<tns:UseGeneralRouting>false</tns:UseGeneralRouting>
									<tns:DynamicRouting>
										<tns:DynamicValue>ISIA01001</tns:DynamicValue>
									</tns:DynamicRouting>
								</tns:RegistryRecordRouting>
							</xsl:for-each>
						</tns:RegistryRouting>
					</tns:RoutingInformation>
				</tns:RequestMetadata>
				<xsl:apply-templates select="//tns:RequestContent"/>
			</tns:RequestMessage>
		</tns:ClientMessage>
	</xsl:template>
	<xsl:template match="//tns:RequestContent">
		<xsl:copy-of select="."/>
	</xsl:template>
</xsl:stylesheet>
