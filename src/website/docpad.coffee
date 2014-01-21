module.exports =

  # Template Data
  # =============
  # These are variables that will be accessible via our templates
  # To access one of these within our templates, refer to the FAQ: https://github.com/bevry/docpad/wiki/FAQ

  templateData:

    # Specify some site properties
    site:
      # The production url of our website
      url: "http://spielproject.info"

      # Here are some old site urls that you would like to redirect from
      # oldUrls: [
        # 'www.website.com',
        # 'website.herokuapp.com'
      # ]

      # The default title of our website
      title: "Spiel"

      # The website description (for SEO)
      description: """
        A flexible, scriptable Android screen reader.
        """

      # The website keywords (for SEO) separated by commas
      keywords: """
        place, your, website, keywoards, here, keep, them, related, to, the, content, of, your, website
        """

      # The website author's name
      author: "Nolan Darilek"

      # The website author's email
      email: "nolan@thewordnerd.info"

      # Your company's name
      copyright: "© Nolan Darilek 2009-2014"

    # Helper Functions
    # ----------------

    # Get the prepared site/document title
    # Often we would like to specify particular formatting to our page's title
    # we can apply that formatting here
    getPreparedTitle: ->
      # if we have a document title, then we should use that and suffix the site's title onto it
      if @document.title
        "#{@document.title} | #{@site.title}"
      # if our document does not have it's own title, then we should just use the site's title
      else
        @site.title

    # Get the prepared site/document description
    getPreparedDescription: ->
      # if we have a document description, then we should use that, otherwise use the site's description
      @document.description or @site.description

    # Get the prepared site/document keywords
    getPreparedKeywords: ->
      # Merge the document keywords with the site keywords
      @site.keywords.concat(@document.keywords or []).join(', ')

  # Collections
  # ===========
  # These are special collections that our website makes available to us

  collections:
    pages: (database) ->
      database.findAllLive({type: "page", order: {$exists: true}, isPagedAuto: {$ne: true}}, [order:1,title:1]).on("add", (doc) ->
        doc.setMetaDefaults(layout: "default")
      )

    articles: (database) ->
      database.findAllLive({type:'article'},[date:-1]).on("add", (doc) ->
        doc.setMetaDefaults(layout: "default")
      )

  # DocPad Events
  # =============

  # Here we can define handlers for events that DocPad fires
  # You can find a full listing of events on the DocPad Wiki
  events:

    # Server Extend
    # Used to add our own custom routes to the server before the docpad routes are added
    serverExtend: (opts) ->
      # Extract the server from the options
      {server} = opts
      docpad = @docpad

      # As we are now running in an event,
      # ensure we are using the latest copy of the docpad configuraiton
      # and fetch our urls from it
      latestConfig = docpad.getConfig()
      oldUrls = latestConfig.templateData.site.oldUrls or []
      newUrl = latestConfig.templateData.site.url

      # Redirect any requests accessing one of our sites oldUrls to the new site url
      server.use (req, res, next) ->
        if req.headers.host in oldUrls
          res.redirect 301, newUrl+req.url
        else
          next()
