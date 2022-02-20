<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fssp="urn://x-artifacts-fssp-ru/mvv/smev3/application-documents/1.1.1" xmlns:c="urn://x-artifacts-fssp-ru/mvv/smev3/container/1.1.0" xmlns:uuid="java.util.UUID">
	<xsl:output method="xml" encoding="UTF-8"/>
	<xsl:param name="Timestamp"/>
	<xsl:template match="/">
		<tns:ClientMessage xmlns:tns="urn://x-artefacts-smev-gov-ru/services/service-adapter/types">
			<tns:itSystem>FSOR01_3T</tns:itSystem>
			<tns:ResponseMessage>
				<tns:ResponseMetadata>
					<tns:clientId>
						<xsl:variable name="uuid" select="uuid:randomUUID()"/>
						<xsl:value-of select="$uuid"/>
					</tns:clientId>
					<tns:replyToClientId>
						<xsl:value-of select="/tns:QueryResult/tns:Message/tns:RequestMetadata/tns:clientId"/>
					</tns:replyToClientId>
				</tns:ResponseMetadata>
				<tns:ResponseContent>
					<tns:content>
						<tns:MessagePrimaryContent>
							<xsl:apply-templates select="//fssp:ApplicationDocumentsRequest"/>
						</tns:MessagePrimaryContent>
					</tns:content>
				</tns:ResponseContent>
			</tns:ResponseMessage>
		</tns:ClientMessage>
	</xsl:template>
	<xsl:template match="//fssp:ApplicationDocumentsRequest">
		<fssp:ApplicationDocumentsResponse xmlns:fssp="urn://x-artifacts-fssp-ru/mvv/smev3/application-documents/1.1.1" xmlns:c="urn://x-artifacts-fssp-ru/mvv/smev3/container/1.1.0">
			<c:ID>
				<xsl:variable name="uuid" select="uuid:randomUUID()"/>
				<xsl:value-of select="$uuid"/>
			</c:ID>
			<c:ReferencedPackId>
				<xsl:value-of select="./c:ID"/>
			</c:ReferencedPackId>
			<c:Date>
				<xsl:value-of select="$Timestamp"/>
			</c:Date>
			<c:ReceiptResult>SUCCESS</c:ReceiptResult>
			<xsl:apply-templates select="//c:Document"/>
		</fssp:ApplicationDocumentsResponse>
	</xsl:template>
	<xsl:template match="//c:Document">
		<c:DocumentReceipt>
			<c:ID>
				<xsl:variable name="uuid" select="uuid:randomUUID()"/>
				<xsl:value-of select="$uuid"/>
			</c:ID>
			<c:ReferencedDocumentId>
				<xsl:value-of select="./c:ID"/>
			</c:ReferencedDocumentId>
			<c:ReceiptDate>
				<xsl:value-of select="$Timestamp"/>
			</c:ReceiptDate>
			<c:ReceiptId>93</c:ReceiptId>
		</c:DocumentReceipt>
	</xsl:template>
</xsl:stylesheet>
