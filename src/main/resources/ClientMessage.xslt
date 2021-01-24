<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
	<xsl:output method="xml" encoding="UTF-8"/>
	<xsl:param name="ClientID"/>
	<xsl:param name="PersonalSign"/>
	<xsl:param name="AttachmentFile"/>
	<xsl:param name="AttachmentSign"/>
	<xsl:template match="/">
		<tns:ClientMessage xmlns:tns="urn://x-artefacts-smev-gov-ru/services/service-adapter/types">
			<tns:itSystem>FSOR01_3S</tns:itSystem>
			<tns:RequestMessage>
				<tns:RequestMetadata>
					<tns:clientId>
						<xsl:value-of select="$ClientID"/>
					</tns:clientId>
					<tns:testMessage>true</tns:testMessage>
				</tns:RequestMetadata>
				<tns:RequestContent>
					<tns:content>
						<tns:MessagePrimaryContent>
							<xsl:copy-of select="/"/>
						</tns:MessagePrimaryContent>
						<xsl:if test="string-length($PersonalSign) != 0">
							<tns:PersonalSignature>
								<xsl:copy-of select="document($PersonalSign)"/>
							</tns:PersonalSignature>
						</xsl:if>
						<xsl:if test="string-length($AttachmentFile) != 0">
							<tns:AttachmentHeaderList>
								<tns:AttachmentHeader>
									<tns:filePath>
										<xsl:value-of select="$AttachmentFile"/>
									</tns:filePath>
									<xsl:if test="string-length($AttachmentSign) != 0">
										<tns:SignaturePKCS7>
											<xsl:copy-of select="$AttachmentSign"/>
										</tns:SignaturePKCS7>
									</xsl:if>
									<tns:TransferMethod>MTOM</tns:TransferMethod>
								</tns:AttachmentHeader>
							</tns:AttachmentHeaderList>
						</xsl:if>
					</tns:content>
				</tns:RequestContent>
			</tns:RequestMessage>
		</tns:ClientMessage>
	</xsl:template>
</xsl:stylesheet>
