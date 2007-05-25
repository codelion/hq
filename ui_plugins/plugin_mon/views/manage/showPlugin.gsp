<h3>Plugin information for <%= h plugin.name %></h3>

<%= link_to '<-- Back to Index', [action:'index'] %>

<br/>
<br/>
<br/>
Views
<table>
<thead>
  <tr>
    <th>Path</th>
    <th>Attachable to</th>
  </tr>
</thead>
<tbody>
<% for (v in plugin.views) { %>
  <tr>
    <td><%= h v.path %></td>
    <td><%= h v.attachType.description %></td>
  </tr>
<% } %>
</tbody>
</table>
