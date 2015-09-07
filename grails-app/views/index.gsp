<!DOCTYPE html>
<html>
	<head>
		<meta name="layout" content="${grailsApplication.config.skin.layout}"/>
		<title>BIE Web services | ${grailsApplication.config.skin.orgNameLong}</title>
	</head>
	<body>
		<div id="page-body" role="main">
			<h1>BIE web services</h1>
			<p>Here is a listing of web services for BIE</p>
			<table class="table">
				<tr>
					<td>Taxon details</td>
					<td>${g.createLink(controller: 'search', action: 'taxon')}/{GUID}</td>
				</tr>
				<tr>
					<td>Taxon details - simple</td>
					<td>${g.createLink(controller: 'search', action: 'shortProfile')}/{GUID}</td>
				</tr>
				<tr>
					<td>Search</td>
					<td>${g.createLink(controller: 'search', action: 'search')}?q={search terms}</td>
				</tr>
				<tr>
					<td>Autocomplete</td>
					<td>${g.createLink(controller: 'search', action: 'auto')}?q={search terms}</td>
				</tr>
				<tr>
					<td>Classification</td>
					<td>${g.createLink(controller: 'search', action: 'classification')}?q={search terms}</td>
				</tr>
				<tr>
					<td>Child concepts</td>
					<td>${g.createLink(controller: 'search', action: 'childConcepts')}?q={search terms}</td>
				</tr>
				<tr>
					<td>Image search</td>
					<td>${g.createLink(controller: 'search', action: 'imageSearch')}/{GUID}</td>
				</tr>
				<tr>
					<td>Bulk lookup by GUID</td>
					<td>${g.createLink(controller: 'search', action: 'bulkGuidLookup')}/{GUID}</td>
				</tr>
			</table>
		</div>
	</body>
</html>
