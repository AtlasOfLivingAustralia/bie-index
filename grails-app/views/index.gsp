<!DOCTYPE html>
<html>
	<head>
		<meta name="layout" content="${grailsApplication.config.skin.layout}"/>
		<title>BIE Web services | ${grailsApplication.config.skin.orgNameLong}</title>
	</head>
	<body>
		<div id="page-body" role="main">
			<h1>BIE web services</h1>
			<p class="lead">
				Web services for searching taxonomic concepts and general search across the system.
			</p>
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
					<td>${g.createLink(controller: 'search', action: 'classification')}/{GUID}</td>
				</tr>
				<tr>
					<td>Child concepts</td>
					<td>${g.createLink(controller: 'search', action: 'childConcepts')}/{GUID}</td>
				</tr>
				<tr>
					<td>Image search</td>
					<td>${g.createLink(controller: 'search', action: 'imageSearch')}/{GUID}</td>
				</tr>
				<tr>
					<td>Bulk lookup by GUID</td>
					<td>${g.createLink(controller: 'search', action: 'bulkGuidLookup')} POST JSON [ "guid", ...]</td>
				</tr>
				<tr>
					<td>Bulk lookup by name</td>
					<td>${g.createLink(controller: 'search', action: 'speciesLookupBulk')} POST JSON { names: [ "name", ... ], "vernacular": fase }</td>
				</tr>
				<tr>
					<td>Habitat list</td>
					<td>${g.createLink(controller: 'search', action: 'habitats')} </td>
				</tr>
				<tr>
					<td>Habitat tree</td>
					<td>${g.createLink(controller: 'search', action: 'habitatTree')} </td>
				</tr>
				<tr>
					<td>Habitats for species</td>
					<td>${g.createLink(controller: 'search', action: 'getHabitatIDs')}/{GUID}</td>
				</tr>
				<tr>
					<td>Habitat details</td>
					<td>${g.createLink(controller: 'search', action: 'getHabitat')}/{GUID}</td>
				</tr>
				<tr>
					<td>Recognised ranks</td>
					<td>${g.createLink(controller: 'import', action: 'ranks')}</td>
				</tr>
			</table>
		</div>
	</body>
</html>
