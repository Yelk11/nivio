import React, { useState, ReactElement } from 'react';
import Grid from '@material-ui/core/Grid';
import { Button, Box } from '@material-ui/core';
import { Link } from 'react-router-dom';
import Events from '../EventComponent/Events';
import GenericModal from '../ModalComponent/GenericModal';

import './Navigation.scss';

/**
 * Header Component
 */
const Navigation: React.FC = () => {
  const [modalContent, setModalContent] = useState<string | ReactElement | ReactElement[] | null>(
    null
  );

  const showEvents = () => {
    setModalContent(<Events />);
  };

  return (
    <div className='navigationContainer'>
      <GenericModal modalContent={modalContent} />
      <Grid container spacing={2} className={'header'}>
        <Grid item xs={8} sm={9} className={'first'}></Grid>
        <Grid item xs={3} sm={2} className={'title'}>
          Nivio
        </Grid>
        <Grid item xs={1} sm={1} className={'last'}></Grid>
      </Grid>

      <Grid
        component={Box}
        container
        spacing={2}
        className={'main'}
        display={{ xs: 'none', sm: 'flex', lg: 'flex' }}
      >
        <Grid item xs={2} sm={1} className={'elbow1'}></Grid>
        <Grid item xs={1} sm={11} className={'elbow2'}>
          <div className={'elbow-outer'}>
            <div className={'elbow-inner'}></div>
          </div>
        </Grid>
      </Grid>

      <Grid container spacing={2} className={'main'}>
        <Grid
          component={Box}
          item
          xs={3}
          sm={1}
          lg={1}
          className={'sidebar'}
          display={{ xs: 'none', sm: 'block', lg: 'block' }}
        >
          <div className={'spacer'}></div>

          <Button component={Link} to={``} fullWidth className={'button stackedButton'}>
            Home
          </Button>

          <Button component={Link} to={``} fullWidth className={'button stackedButton'}>
            Guide
          </Button>

          <Button
            data-testid='ManualButton'
            component={Link}
            to={`/man/install`}
            fullWidth
            className={'button stackedButton'}
          >
            Manual
          </Button>

          <Button
            data-testid='EventsButton'
            onClick={() => showEvents()}
            fullWidth
            className={'button'}
          >
            Events
          </Button>

          <div className={'spacer'}></div>
        </Grid>
        <Grid item xs={'auto'} sm={1} lg={1} className={'spacer'}></Grid>
        <Grid item xs={12} sm={10} lg={10}></Grid>
      </Grid>
    </div>
  );
};

export default Navigation;
